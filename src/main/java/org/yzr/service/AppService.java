package org.yzr.service;


import org.apache.commons.io.FileUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.yzr.dao.AppDao;
import org.yzr.dao.PackageDao;
import org.yzr.model.App;
import org.yzr.model.Package;
import org.yzr.utils.CodeGenerator;
import org.yzr.utils.PathManager;
import org.yzr.vo.AppViewModel;
import org.yzr.vo.PackageViewModel;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AppService {
    @Resource
    private AppDao appDao;
    @Resource
    private PathManager pathManager;
    @Resource
    private PackageService packageService;
    @Resource
    private PackageDao packageDao;

    @Transactional
    public App save(App app) {
        App app1 = this.appDao.save(app);
        app1.getCurrentPackage();
        try {
            // 触发级联查询
            app1.getWebHookList().forEach(webHook -> {});
        } catch (Exception e) {
            e.printStackTrace();
        }
        return app1;
    }

    @Transactional
    public List<AppViewModel> findAll(HttpServletRequest request) {
        Iterable<App> apps = this.appDao.findAll();
        List<AppViewModel> list = new ArrayList<>();
        for (App app : apps) {
            AppViewModel appViewModel = new AppViewModel(app, this.pathManager, false, request);
            list.add(appViewModel);
        }
        return list;
    }

    @Transactional
    public AppViewModel getById(String appID, HttpServletRequest request) {
        Optional<App> optionalApp = this.appDao.findById(appID);
        App app = optionalApp.get();
        if (app != null) {
            app.getPackageList().forEach(aPackage -> {});
            AppViewModel appViewModel = new AppViewModel(app, this.pathManager, true, request);
            return appViewModel;
        }
        return null;
    }

    @Transactional
    public App getByPackage(Package aPackage) {
        App app = this.appDao.get(aPackage.getBundleID(), aPackage.getPlatform());
        if (app == null) {
            app = new App();
            String shortCode = CodeGenerator.generate(4);
            while (this.appDao.findByShortCode(shortCode) != null) {
                shortCode = CodeGenerator.generate(4);
            }
            BeanUtils.copyProperties(aPackage, app);
            app.setShortCode(shortCode);
        } else {
            app.setName(aPackage.getName());
            // 触发级联查询
            app.getPackageList().forEach(p->{});
            app.getWebHookList().forEach(webHook -> {});
        }
        if (app.getPackageList() == null) {
            app.setPackageList(new ArrayList<>());
        }
        return app;
    }

    @Transactional
    public void deleteById(String id) {
        App app = this.appDao.findById(id).get();
        if (app != null) {
            this.appDao.deleteById(id);
            // 消除整个 APP 目录
            String path = PathManager.getAppPath(app);
            PathManager.deleteDirectory(path);
        }

    }

    /**
     * 通过 code 和 packageId 查询
     * @param code
     * @param packageId
     * @return
     */
    @Transactional
    public AppViewModel findByCode(String code, String packageId, HttpServletRequest request) {
        App app = this.appDao.findByShortCode(code);
        AppViewModel viewModel = new AppViewModel(app, pathManager, packageId, request);
        return viewModel;
    }

    /**
     * 清理指定 App 的过期非定版包(删数据库记录 + 删安装包文件)。
     * 保留:定版包、N 天内的包、当前展示包(currentPackage)。
     * @param appId App ID
     * @param days  清理多少天以前的包,夹紧到 3-30
     * @return 实际清理的包数量
     */
    /**
     * 注意:本方法不加 @Transactional。App.packageList 是 cascade=ALL,
     * 若在一个持有托管 App 的事务里删子包,提交时会被级联重新保存。
     * 因此这里先用只读查询取候选 id,再逐个交给 packageService.deleteById
     * (各自独立事务、不持有托管 App),与单个删除 /p/delete 的行为一致。
     */
    public int cleanExpiredPackages(String appId, int days) {
        if (days < 3) days = 3;
        if (days > 30) days = 30;
        String currentId = this.appDao.findCurrentPackageId(appId);
        long threshold = System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000;
        List<String> targets = new ArrayList<>();
        // 逐个打印被删的包 —— 删除会连磁盘文件一起清掉，不可逆；
        // 没有明细日志的话，事后无法回答「到底删了什么」。手动点「清除」时同样受益。
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (Package aPackage : this.packageDao.findByAppId(appId)) {
            if (!aPackage.getIsRelease()
                    && aPackage.getCreateTime() < threshold
                    && !aPackage.getId().equals(currentId)) {
                targets.add(aPackage.getId());
                System.out.println("[cleanup]     - " + aPackage.getBundleID()
                        + " v" + aPackage.getVersion() + "(" + aPackage.getBuildVersion() + ")"
                        + " commit=" + (aPackage.getGitCommit() == null ? "-" : aPackage.getGitCommit())
                        + " " + fmt.format(new java.util.Date(aPackage.getCreateTime()))
                        + " " + (aPackage.getSize() / 1048576) + "MB"
                        + " extra=" + (aPackage.getExtra() == null ? "-" : aPackage.getExtra()));
            }
        }
        for (String id : targets) {
            this.packageService.deleteById(id);
        }
        return targets.size();
    }
}
