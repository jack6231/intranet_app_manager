package org.yzr.utils.parser;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.IconFace;
import org.apache.commons.io.FileUtils;
import org.yzr.model.Package;
import org.yzr.utils.PathManager;
import net.dongliu.apk.parser.bean.*;
import java.io.File;
import java.util.UUID;

public class APKParser implements PackageParser {
    @Override
    public Package parse(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) return null;
            ApkFile apkFile = new ApkFile(file);
            long currentTimeMillis = System.currentTimeMillis();
            Package aPackage = new Package();
            aPackage.setSize(file.length());
            ApkMeta meta = apkFile.getApkMeta();
            aPackage.setName(meta.getName());
            String version = meta.getVersionName();
            String buildVersion = meta.getVersionCode() + "";
            if (version.length() < 1) {
                version = meta.getPlatformBuildVersionName();
            }
            if (buildVersion.length() < 1) {
                buildVersion = meta.getPlatformBuildVersionCode();
            }
            aPackage.setVersion(version);
            aPackage.setBuildVersion(buildVersion);
            aPackage.setBundleID(meta.getPackageName());
            aPackage.setMinVersion(meta.getMinSdkVersion());
            aPackage.setPlatform("android");
            aPackage.setCreateTime(currentTimeMillis);
            int iconCount = apkFile.getAllIcons().size();
            for (int i = iconCount-1; i >= 0; i--)  {
                IconFace icon = apkFile.getAllIcons().get(i);
                if (icon instanceof Icon) {
                    String iconPath = PathManager.getTempIconPath(aPackage);
                    File iconFile = new File(iconPath);
                    FileUtils.writeByteArrayToFile(iconFile, icon.getData());
                    break;
                }
            }
            apkFile.close();
            return aPackage;
        }catch (Exception e){

        }
        return null;
    }
}
