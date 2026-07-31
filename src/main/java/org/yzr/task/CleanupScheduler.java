package org.yzr.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.yzr.dao.AppDao;
import org.yzr.model.App;
import org.yzr.service.AppService;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 定时清理过期安装包。
 *
 * 复用页面上「清除」按钮走的同一个方法 {@link AppService#cleanExpiredPackages(String, int)}，
 * 所以自动清理和手动清理的删除口径完全一致，不会出现两套规则。
 *
 * 删除条件（在 cleanExpiredPackages 里）：
 *   未定版(isRelease=false) 且 早于阈值 且 不是该 app 的 currentPackage
 * → **点了「定版」的发版包永远不会被自动删**，这是短保留期能安全落地的前提。
 *
 * 配置（application.properties）：
 *   app.cleanup.enabled  开关，false 则整个任务跳过
 *   app.cleanup.days     保留天数；cleanExpiredPackages 内部会夹到 3~30
 *   app.cleanup.cron     触发时间（Spring 6 位 cron：秒 分 时 日 月 周）
 */
@Component
public class CleanupScheduler {

    @Resource
    private AppDao appDao;
    @Resource
    private AppService appService;

    @Value("${app.cleanup.enabled:true}")
    private boolean enabled;

    @Value("${app.cleanup.days:7}")
    private int days;

    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Scheduled(cron = "${app.cleanup.cron:0 0 3 * * *}")
    public void cleanExpired() {
        String now = TS.format(new Date());
        if (!enabled) {
            System.out.println("[cleanup] " + now + " 已被 app.cleanup.enabled=false 关闭，跳过");
            return;
        }
        System.out.println("[cleanup] " + now + " 开始：保留 " + days + " 天内的包"
                + "（定版包与各 app 当前包不删）");

        int totalApps = 0;
        int totalRemoved = 0;
        int failedApps = 0;

        for (App app : this.appDao.findAll()) {
            totalApps++;
            try {
                int removed = this.appService.cleanExpiredPackages(app.getId(), days);
                totalRemoved += removed;
                if (removed > 0) {
                    System.out.println("[cleanup]   " + app.getName() + " (" + app.getPlatform()
                            + ", " + app.getBundleID() + ") 删除 " + removed + " 个");
                }
            } catch (Exception e) {
                // 单个 app 失败不能中断整轮 —— 否则一个坏数据会让后面的 app 永远清不到
                failedApps++;
                System.out.println("[cleanup]   ✗ " + app.getName() + " (" + app.getId()
                        + ") 清理失败: " + e.getMessage());
            }
        }

        System.out.println("[cleanup] " + TS.format(new Date()) + " 结束：扫描 " + totalApps
                + " 个 app，删除 " + totalRemoved + " 个包"
                + (failedApps > 0 ? "，失败 " + failedApps + " 个 app" : ""));
    }
}
