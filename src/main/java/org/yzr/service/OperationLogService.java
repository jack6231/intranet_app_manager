package org.yzr.service;

import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yzr.dao.AppDao;
import org.yzr.dao.PackageDao;
import org.yzr.model.App;
import org.yzr.model.Package;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 页面操作留痕：只追加写日志文件（默认工作目录下 operation.log），不展示、不入库，追查时 grep 该文件。
 *
 * 每行一条，字段用 tab 分隔：时间 / 操作 / 对象 / 对象id / 结果 / IP / 主机名 / X-Forwarded-For / User-Agent。
 * 平台没有登录，记不到账号；IP 由 DHCP 分配，落到具体的人需对照 IT 的租约记录。
 * IP 反查主机名可能要等 DNS 超时，放到后台单线程做，同时保证写文件不并发。
 */
@Service
public class OperationLogService {

    public static final String ASC_UPLOAD = "上传 App Store";
    public static final String DELETE_PACKAGE = "删除包";
    public static final String RELEASE = "定版";
    public static final String UNRELEASE = "取消定版";
    public static final String CLEAN = "清理过期包";
    public static final String DELETE_APP = "删除应用";

    @Resource
    private PackageDao packageDao;
    @Resource
    private AppDao appDao;

    @Value("${app.operation-log:operation.log}")
    private String logPath;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "operation-log");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    /** 包的可读描述，如「com.Trucker-Path-Pro-Dev 7.1.7 (Build 71700) release_bj #1116」；操作前调用，删除后仍可读 */
    public String describePackage(String packageId) {
        try {
            Package p = packageDao.findById(packageId).orElse(null);
            if (p == null) {
                return "包不存在";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(p.getBundleID()).append(' ').append(p.getVersion())
              .append(" (Build ").append(p.getBuildVersion()).append(')');
            if (StringUtils.hasLength(p.getExtra())) {
                Map<?, ?> extra = (Map<?, ?>) JSON.parse(p.getExtra());
                if (extra.get("jobName") != null) {
                    sb.append(' ').append(extra.get("jobName"));
                }
                if (extra.get("buildNumber") != null) {
                    sb.append(" #").append(extra.get("buildNumber"));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "描述失败: " + e.getMessage();
        }
    }

    /** 应用的可读描述，如「Trucker Path（ios com.Trucker-Path-Pro）」 */
    public String describeApp(String appId) {
        try {
            App app = appDao.findById(appId).orElse(null);
            return app == null ? "应用不存在"
                    : app.getName() + "（" + app.getPlatform() + " " + app.getBundleID() + "）";
        } catch (Exception e) {
            return "描述失败: " + e.getMessage();
        }
    }

    /** 记录一次操作；任何异常只打印，不影响业务操作本身 */
    public void record(HttpServletRequest request, String action, String targetId, String target, String result) {
        try {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String ip = request.getRemoteAddr();
            String forwardedFor = request.getHeader("X-Forwarded-For");
            String userAgent = request.getHeader("User-Agent");
            executor.submit(() -> append(formatLine(time, action, target, targetId, result,
                    ip, resolveHostname(ip), forwardedFor, userAgent)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void append(String line) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(logPath, true), StandardCharsets.UTF_8)) {
            w.write(line);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 一行日志：字段 tab 分隔，字段内的换行和 tab 替换成空格，空值写 - */
    static String formatLine(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append('\t');
            }
            String f = fields[i];
            sb.append(f == null || f.isEmpty() ? "-" : f.replaceAll("[\\t\\r\\n]+", " "));
        }
        return sb.append('\n').toString();
    }

    private static String resolveHostname(String ip) {
        try {
            return InetAddress.getByName(ip).getCanonicalHostName();
        } catch (Exception e) {
            return ip;
        }
    }
}
