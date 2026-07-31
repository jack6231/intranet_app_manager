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
        List<Package> packages = (bundleID == null || bundleID.trim().isEmpty())
                ? this.packageDao.findByCommit(commit)
                : this.packageDao.findByBundleIDAndCommit(bundleID.trim(), commit);
        List<PackageViewModel> result = new ArrayList<>();
        if (packages != null) {
            for (Package aPackage : packages) {
                result.add(new PackageViewModel(aPackage, this.pathManager, request));
            }
        }
        return result;
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
