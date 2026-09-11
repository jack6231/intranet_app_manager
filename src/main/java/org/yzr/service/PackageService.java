package org.yzr.service;


import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.yzr.dao.PackageDao;
import org.yzr.model.Package;
import org.yzr.utils.ImageUtils;
import org.yzr.utils.PathManager;
import org.yzr.utils.parser.ParserClient;
import org.yzr.vo.PackageViewModel;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class PackageService {

    @Resource
    private PackageDao packageDao;
    @Resource
    private PathManager pathManager;

    public Package buildPackage(String filePath) {
        Package aPackage = ParserClient.parse(filePath);
        try {
            String fileName = aPackage.getPlatform() + "." + FilenameUtils.getExtension(filePath);
            // 更新文件名
            aPackage.setFileName(fileName);

            String packagePath = PathManager.getFullPath(aPackage);
            String tempIconPath = PathManager.getTempIconPath(aPackage);
            String iconPath = packagePath + File.separator + "icon.png";
            String sourcePath = packagePath + File.separator + fileName;

            // 拷贝图标
            ImageUtils.resize(tempIconPath, iconPath, 192, 192);
            // 源文件
            FileUtils.copyFile(new File(filePath), new File(sourcePath));

            // 删除临时图标
            FileUtils.forceDelete(new File(tempIconPath));
            // 源文件
            FileUtils.forceDelete(new File(filePath));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return aPackage;
    }

    @Transactional
    public Package save(Package aPackage) {
        return this.packageDao.save(aPackage);
    }

    @Transactional
    public Package get(String id) {
        Package aPackage = this.packageDao.findById(id).get();
        return aPackage;
    }

    @Transactional
    public PackageViewModel findById(String id, HttpServletRequest request) {
        Package aPackage = this.packageDao.findById(id).get();
        PackageViewModel viewModel = new PackageViewModel(aPackage, this.pathManager, request);
        return viewModel;
    }

    /**
     * 按 git commit 找包，最新的在前。
     *
     * 给 CI 用：拿到目标 commit 后先问这里有没有现成的包，命中就直接下载安装，省掉一次编译。
     * bundleID 传空则跨应用查。commit 的长度差异由 dao 层做双向前缀匹配处理。
     */
    @Transactional
    public List<PackageViewModel> findByCommit(String bundleID, String commit, HttpServletRequest request) {
        return findByCommit(bundleID, commit, null, request);
    }

    /**
     * 同上，再按 iOS 包的 E2E 构建标记过滤。
     *
     * @param tpE2EBuild 传 "1" 只要 TP_E2E 构建的包（Info.plist TPE2EBuild=1，带沙盒覆盖文件加载器）；
     *                   传空/null 不过滤，保持原行为。同一 commit 通常同时有打包机的 Release 包和
     *                   E2E Job 自编的 Debug 包，运行器必须拿到后者，否则 plist 预置只能退回 legacy 模式。
     */
    @Transactional
    public List<PackageViewModel> findByCommit(String bundleID, String commit, String tpE2EBuild,
                                               HttpServletRequest request) {
        List<Package> packages = (bundleID == null || bundleID.trim().isEmpty())
                ? this.packageDao.findByCommit(commit)
                : this.packageDao.findByBundleIDAndCommit(bundleID.trim(), commit);
        packages = filterByE2EBuild(packages, tpE2EBuild);
        List<PackageViewModel> result = new ArrayList<>();
        if (packages != null) {
            for (Package aPackage : packages) {
                result.add(new PackageViewModel(aPackage, this.pathManager, request));
            }
        }
        return result;
    }

    /** tpE2EBuild 为空则原样返回；否则只保留 getTpE2EBuild() 与之相等的包（null 视为不相等）。纯函数，便于单测。 */
    static List<Package> filterByE2EBuild(List<Package> packages, String tpE2EBuild) {
        if (packages == null || tpE2EBuild == null || tpE2EBuild.trim().isEmpty()) {
            return packages;
        }
        String want = tpE2EBuild.trim();
        List<Package> out = new ArrayList<>();
        for (Package p : packages) {
            if (want.equals(p.getTpE2EBuild())) {
                out.add(p);
            }
        }
        return out;
    }

    @Transactional
    public void deleteById(String id) {
        Package aPackage = this.packageDao.findById(id).get();
        if (aPackage != null) {
            this.packageDao.deleteById(id);
            String path = PathManager.getFullPath(aPackage);
            PathManager.deleteDirectory(path);
        }

    }

    @Transactional
    public void updateById(String id, boolean isRelease) {
        Package aPackage = this.packageDao.findById(id).get();
        if (aPackage != null) {
            aPackage.setIsRelease(isRelease);
            this.packageDao.save(aPackage);
        }

    }
}
