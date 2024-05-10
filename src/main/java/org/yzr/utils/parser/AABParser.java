/**
 * bundletool下载地址： https://github.com/google/bundletool/releases
 * 需要将 bundletool 放在用户根目录下
 */
package org.yzr.utils.parser;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.IconFace;
import net.dongliu.apk.parser.bean.Icon;
import org.apache.commons.io.FileUtils;
import org.yzr.model.Package;
import org.yzr.utils.ExternalCommandHelper;
import org.yzr.utils.PathManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class AABParser implements PackageParser {
    @Override
    public Package parse(String filePath) {
        try {
            File aabFile = new File(filePath);
            if (!aabFile.exists()) {
                return null;
            }

            String userHome = System.getProperty("user.home");
            String outputApksPath = Paths.get(userHome, "output.apks").toString();
            String bundleToolJarPath = Paths.get(userHome, "bundletool.jar").toString();

            String bundleToolCommand = String.format("java -jar %s build-apks --bundle=%s --output=%s --mode=universal --overwrite",
                    bundleToolJarPath, filePath, outputApksPath);
            ExternalCommandHelper commandHelper = new ExternalCommandHelper();
            boolean isCommandSuccess = commandHelper.executeCommand(bundleToolCommand);
            if (!isCommandSuccess) {
                System.err.println("Failed to execute bundletool command.");
                return null;
            }
            // 解压 output.apks 以获取 universal.apk
            String universalApkPath = extractUniversalAPK(outputApksPath, userHome);
            if (universalApkPath == null) {
                System.err.println("Failed to extract universal APK.");
                return null;
            }

            File file = new File(universalApkPath);
            if (!file.exists())  {
                return null;
            }
            // 将 aab 转成 apk 文件
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
            e.printStackTrace();
        }
        return null;
    }

    private String extractUniversalAPK(String zipFilePath, String outputDir) throws IOException {
        ZipFile zipFile = new ZipFile(zipFilePath);
        ZipEntry entry = zipFile.getEntry("universal.apk"); // 根据实际路径调整
        if (entry != null) {
            Path outputPath = Paths.get(outputDir, "universal.apk");
            Files.copy(zipFile.getInputStream(entry), outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            zipFile.close();
            return outputPath.toString();
        }
        zipFile.close();
        return null;
    }
}