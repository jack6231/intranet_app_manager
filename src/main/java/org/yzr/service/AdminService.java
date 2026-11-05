package org.yzr.service;

import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yzr.dao.PackageDao;
import org.yzr.model.Account;
import org.yzr.model.Package;
import org.yzr.vo.AdminAccountRow;
import org.yzr.vo.AscUploadRow;
import org.yzr.vo.OperationLogEntry;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理页的数据：账号列表、操作记录筛选、App Store 上传记录。只读，删除账号走 AccountService。
 */
@Service
public class AdminService {

    @Resource
    private AccountService accountService;
    @Resource
    private PackageDao packageDao;

    /** 账号列表，附带每个账号的操作次数和最近一次操作时间（logs 为最新在前） */
    public List<AdminAccountRow> accountRows(List<OperationLogEntry> logs) {
        List<AdminAccountRow> rows = new ArrayList<>();
        for (Account a : accountService.listAccounts()) {
            List<OperationLogEntry> mine = filterLogs(logs, a.getUsername(), null);
            rows.add(new AdminAccountRow(a.getId(), a.getUsername(), a.isAdmin(), formatTime(a.getCreateTime()),
                    mine.size(), mine.isEmpty() ? null : mine.get(0).getTime()));
        }
        return rows;
    }

    /** 筛选器里的账号选项：现有账号 + 日志里出现过的账号（含已删除的），不含「未登录」 */
    public List<String> logAccounts(List<OperationLogEntry> logs) {
        Set<String> names = new LinkedHashSet<>();
        for (Account a : accountService.listAccounts()) {
            names.add(a.getUsername());
        }
        for (OperationLogEntry e : logs) {
            if (!OperationLogEntry.NO_ACCOUNT.equals(e.getAccount())) {
                names.add(e.getAccount());
            }
        }
        return new ArrayList<>(names);
    }

    /** 按账号、操作筛选；参数为空表示不限。账号传 "-" 筛未登录的操作。纯函数 */
    public static List<OperationLogEntry> filterLogs(List<OperationLogEntry> logs, String account, String action) {
        return logs.stream()
                .filter(e -> !StringUtils.hasLength(account) || account.equals(e.getAccount()))
                .filter(e -> !StringUtils.hasLength(action) || action.equals(e.getAction()))
                .collect(Collectors.toList());
    }

    /** 上传过 App Store 的包：上传中的排最前，其余按上传时间倒序 */
    public List<AscUploadRow> ascUploads() {
        return packageDao.findByAscStatusIsNotNull().stream()
                .sorted(Comparator.comparing((Package p) -> AscUploadService.UPLOADING.equals(p.getAscStatus()) ? 0 : 1)
                        .thenComparing(p -> p.getAscUploadTime() == null ? 0L : p.getAscUploadTime(),
                                Comparator.reverseOrder()))
                .map(AdminService::toAscRow)
                .collect(Collectors.toList());
    }

    private static AscUploadRow toAscRow(Package p) {
        return new AscUploadRow(p.getId(), p.getName(), p.getBundleID(),
                p.getVersion() + " (Build " + p.getBuildVersion() + ")", jobOf(p.getExtra()),
                p.getAscBuildVersion(), p.getAscStatus(), statusLabel(p.getAscStatus()),
                p.getAscUploader(), formatTime(p.getAscUploadTime()));
    }

    private static String statusLabel(String status) {
        if (AscUploadService.UPLOADED.equals(status)) {
            return "已上传";
        }
        if (AscUploadService.UPLOADING.equals(status)) {
            return "上传中";
        }
        if (AscUploadService.FAILED.equals(status)) {
            return "失败";
        }
        return status;
    }

    /** 打包任务：extra 里的 jobName #buildNumber，如「release_bj #1116」 */
    private static String jobOf(String extra) {
        if (!StringUtils.hasLength(extra)) {
            return null;
        }
        try {
            Map<?, ?> m = (Map<?, ?>) JSON.parse(extra);
            Object job = m.get("jobName");
            Object build = m.get("buildNumber");
            if (job == null) {
                return null;
            }
            return build == null ? job.toString() : job + " #" + build;
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatTime(Long millis) {
        return millis == null || millis <= 0 ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(millis));
    }
}
