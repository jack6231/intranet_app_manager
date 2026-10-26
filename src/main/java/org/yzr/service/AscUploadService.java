package org.yzr.service;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yzr.dao.PackageDao;
import org.yzr.model.Package;
import org.yzr.utils.PathManager;
import org.yzr.utils.RemoteShell;
import org.yzr.utils.ipa.AscScripts;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 上传 App Store Connect。
 *
 * .xcarchive 留在打出它的打包机（234/235 任一台）上，平台只存「节点名 + 路径」：
 *   - 列表页：按节点批量 SSH 探测 archive 是否还在、是不是同一个构建，在才显示上传按钮
 *   - 点击上传：SSH 到该机执行 xcodebuild -exportArchive（destination=upload），
 *     认证用该机 Xcode 已登录的 Apple ID，build 号由 Xcode 自动递增
 * 上传串行执行（单线程），状态落库：UPLOADING → UPLOADED / FAILED，完整日志写包目录 asc_upload.log。
 */
@Service
public class AscUploadService {

    public static final String UPLOADING = "UPLOADING";
    public static final String UPLOADED = "UPLOADED";
    public static final String FAILED = "FAILED";

    static final String LOG_FILE = "asc_upload.log";
    private static final long PROBE_CACHE_MILLIS = 30_000L;
    private static final long PROBE_TIMEOUT_SECONDS = 15L;

    @Resource
    private PackageDao packageDao;

    @Value("${asc.nodes:}")
    private String nodesConfig;
    @Value("${asc.ssh-user:iospub}")
    private String sshUser;
    @Value("${asc.team-id:}")
    private String teamId;
    @Value("${asc.keychain-password:}")
    private String keychainPassword;
    @Value("${asc.upload-timeout-minutes:60}")
    private long uploadTimeoutMinutes;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "asc-upload");
        t.setDaemon(true);
        return t;
    });
    // 包 id → 探测结果（带时间戳），避免每次刷新列表都 SSH
    private final Map<String, CachedProbe> probeCache = new ConcurrentHashMap<>();

    private static class CachedProbe {
        final boolean available;
        final String reason;
        final long time;

        CachedProbe(boolean available, String reason) {
            this.available = available;
            this.reason = reason;
            this.time = System.currentTimeMillis();
        }
    }

    /** 服务重启会打断进行中的上传：把残留的 UPLOADING 标成失败，按钮才能重新出现 */
    @PostConstruct
    public void markInterruptedUploads() {
        try {
            for (Package p : packageDao.findByAscStatus(UPLOADING)) {
                p.setAscStatus(FAILED);
                p.setAscMessage("平台重启，上传被中断；Apple 侧结果未知，请先到 App Store Connect 确认再决定是否重传");
                p.setAscUploadTime(System.currentTimeMillis());
                packageDao.save(p);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 某个 App 下 iOS 包的上传状态。只返回「已有状态」或「登记了 archive」的包。
     * @return 包 id → { status, available, reason, buildVersion, message, uploadTime }
     */
    public Map<String, Map<String, Object>> states(String appId) {
        List<Package> packages = packageDao.findByAppId(appId);
        Map<String, Package> toProbe = new LinkedHashMap<>();
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Package p : packages) {
            if (!"ios".equals(p.getPlatform())) {
                continue;
            }
            boolean hasArchive = StringUtils.hasLength(p.getArchivePath());
            if (p.getAscStatus() == null && !hasArchive) {
                continue;
            }
            result.put(p.getId(), baseState(p));
            if (hasArchive && !UPLOADED.equals(p.getAscStatus()) && !UPLOADING.equals(p.getAscStatus())) {
                toProbe.put(p.getId(), p);
            }
        }
        Map<String, CachedProbe> probes = probe(toProbe.values());
        for (Map.Entry<String, CachedProbe> e : probes.entrySet()) {
            Map<String, Object> state = result.get(e.getKey());
            state.put("available", e.getValue().available);
            state.put("reason", e.getValue().reason);
        }
        return result;
    }

    /**
     * 发起上传（异步）。校验不过抛 IllegalStateException，消息直接给前端提示。
     */
    public synchronized void startUpload(String packageId) {
        Package p = packageDao.findById(packageId)
                .orElseThrow(() -> new IllegalStateException("包不存在"));
        if (!"ios".equals(p.getPlatform())) {
            throw new IllegalStateException("只有 iOS 包可以上传 App Store");
        }
        if (UPLOADED.equals(p.getAscStatus())) {
            throw new IllegalStateException("该包已上传过 App Store");
        }
        if (UPLOADING.equals(p.getAscStatus())) {
            throw new IllegalStateException("该包正在上传");
        }
        if (!StringUtils.hasLength(p.getArchivePath())) {
            throw new IllegalStateException("该包没有登记 .xcarchive，无法上传");
        }
        // 点击时重新探测一次，不用缓存：渲染到点击之间 archive 可能已被打包机清掉
        probeCache.remove(p.getId());
        CachedProbe probe = probe(Collections.singletonList(p)).get(p.getId());
        if (probe == null || !probe.available) {
            throw new IllegalStateException(probe == null ? "探测失败" : probe.reason);
        }
        p.setAscStatus(UPLOADING);
        p.setAscMessage(null);
        packageDao.save(p);
        executor.submit(() -> runUpload(packageId));
    }

    /** 最近一次上传的完整日志；没有返回 null */
    public File logFile(String packageId) {
        Optional<Package> p = packageDao.findById(packageId);
        if (!p.isPresent()) {
            return null;
        }
        File f = new File(PathManager.getFullPath(p.get()) + LOG_FILE);
        return f.exists() ? f : null;
    }

    private void runUpload(String packageId) {
        Package p = packageDao.findById(packageId).orElse(null);
        if (p == null) {
            return;
        }
        String host = hostOf(p.getArchiveNode());
        String script = AscScripts.uploadScript(p.getArchivePath(), teamId, keychainPassword);
        RemoteShell.Result r = RemoteShell.run(sshUser, host, script, uploadTimeoutMinutes * 60);
        writeLog(p, host, r);

        // 上传期间包可能被删除，重新取一次再落状态
        Package latest = packageDao.findById(packageId).orElse(null);
        if (latest == null) {
            return;
        }
        latest.setAscUploadTime(System.currentTimeMillis());
        if (r.ok()) {
            latest.setAscStatus(UPLOADED);
            latest.setAscBuildVersion(AscScripts.parseBuildNumber(r.output));
            latest.setAscMessage(null);
        } else {
            latest.setAscStatus(FAILED);
            latest.setAscMessage(AscScripts.failureSummary(r.output, 1000));
        }
        packageDao.save(latest);
        probeCache.remove(packageId);
    }

    private void writeLog(Package p, String host, RemoteShell.Result r) {
        try {
            String header = "time=" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                    + " node=" + p.getArchiveNode() + " host=" + host
                    + " exit=" + r.exitCode + (r.timedOut ? " (timeout)" : "") + "\n\n";
            FileUtils.writeStringToFile(new File(PathManager.getFullPath(p) + LOG_FILE),
                    header + r.output, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Map<String, Object> baseState(Package p) {
        Map<String, Object> s = new HashMap<>();
        s.put("status", p.getAscStatus());
        s.put("available", false);
        s.put("buildVersion", p.getAscBuildVersion());
        s.put("message", p.getAscMessage());
        if (p.getAscUploadTime() != null && p.getAscUploadTime() > 0) {
            s.put("uploadTime", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(p.getAscUploadTime())));
        }
        return s;
    }

    /** 按节点分组，每个节点一次 SSH 批量探测；节点不可达时该组全部视为不可用 */
    private Map<String, CachedProbe> probe(Collection<Package> packages) {
        Map<String, CachedProbe> result = new HashMap<>();
        Map<String, Map<String, String>> byHost = new HashMap<>();
        Map<String, Package> byId = new HashMap<>();
        long now = System.currentTimeMillis();
        for (Package p : packages) {
            CachedProbe cached = probeCache.get(p.getId());
            if (cached != null && now - cached.time < PROBE_CACHE_MILLIS) {
                result.put(p.getId(), cached);
                continue;
            }
            String host = hostOf(p.getArchiveNode());
            if (host == null) {
                result.put(p.getId(), new CachedProbe(false, "未知节点 " + p.getArchiveNode()));
                continue;
            }
            byHost.computeIfAbsent(host, h -> new LinkedHashMap<>()).put(p.getId(), p.getArchivePath());
            byId.put(p.getId(), p);
        }
        for (Map.Entry<String, Map<String, String>> e : byHost.entrySet()) {
            RemoteShell.Result r = RemoteShell.run(sshUser, e.getKey(),
                    AscScripts.probeScript(e.getValue()), PROBE_TIMEOUT_SECONDS);
            Map<String, AscScripts.Probe> parsed = AscScripts.parseProbe(r.output);
            for (String id : e.getValue().keySet()) {
                CachedProbe cp = toCachedProbe(byId.get(id), parsed.get(id), r, e.getKey());
                probeCache.put(id, cp);
                result.put(id, cp);
            }
        }
        return result;
    }

    private static CachedProbe toCachedProbe(Package p, AscScripts.Probe probe, RemoteShell.Result r, String host) {
        if (probe == null) {
            return new CachedProbe(false, "打包机 " + host + " 不可达" + (r.timedOut ? "（超时）" : ""));
        }
        if (!probe.exists) {
            return new CachedProbe(false, ".xcarchive 已被打包机清理");
        }
        if (!AscScripts.sameBuild(p.getGitCommit(), probe.commit)) {
            return new CachedProbe(false, ".xcarchive 已被其他构建覆盖（commit " + probe.commit + "）");
        }
        return new CachedProbe(true, null);
    }

    private String hostOf(String node) {
        return parseNodes(nodesConfig).get(node);
    }

    /** "Jenkins-01=10.2.54.234,Jenkins-02=10.2.54.235" → map */
    static Map<String, String> parseNodes(String config) {
        Map<String, String> map = new HashMap<>();
        if (config == null) {
            return map;
        }
        for (String pair : config.split(",")) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length == 2 && !kv[0].trim().isEmpty() && !kv[1].trim().isEmpty()) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }
}
