package org.yzr.vo;

/** 管理页「App Store 上传」列表的一行：哪个包、谁传的、结果如何 */
public class AscUploadRow {

    private final String packageId;
    private final String appName;
    private final String bundleID;
    private final String version;
    private final String job;
    private final String ascBuildVersion;
    private final String status;
    private final String statusLabel;
    private final String uploader;
    private final String uploadTime;

    public AscUploadRow(String packageId, String appName, String bundleID, String version, String job,
                        String ascBuildVersion, String status, String statusLabel, String uploader, String uploadTime) {
        this.packageId = packageId;
        this.appName = appName;
        this.bundleID = bundleID;
        this.version = version;
        this.job = job;
        this.ascBuildVersion = ascBuildVersion;
        this.status = status;
        this.statusLabel = statusLabel;
        this.uploader = uploader;
        this.uploadTime = uploadTime;
    }

    public String getPackageId() {
        return packageId;
    }

    public String getAppName() {
        return appName;
    }

    public String getBundleID() {
        return bundleID;
    }

    public String getVersion() {
        return version;
    }

    public String getJob() {
        return job;
    }

    public String getAscBuildVersion() {
        return ascBuildVersion;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getUploader() {
        return uploader;
    }

    public String getUploadTime() {
        return uploadTime;
    }
}
