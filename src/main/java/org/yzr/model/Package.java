package org.yzr.model;


import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

@Entity
@Table(name="tb_package")
public class Package {
    // 主键
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;
    // 应用ID
    private String bundleID;
    // 名称
    private String name;
    // 版本
    private String version;
    // 构建版本
    private String buildVersion;
    // 创建时间
    private long createTime;
    // 包大小
    private long size;
    // 最低支持版本
    private String minVersion;
    // 平台(Android 或 iOS)
    private String platform;
    // 扩展消息 (json格式)
    private String extra;
    // 构建该包的 git commit（iOS 取 Info.plist 的 GIT_COMMIT_HASH，Android 取 manifest 的 GIT_COMMIT_HASH meta-data）
    // 可能是短 hash 也可能是全 SHA，查询时做双向前缀匹配。历史包为 null。
    @Column(length = 64)
    private String gitCommit;
    // iOS Info.plist 的 TPE2EBuild：TP_E2E 构建（Debug 配置）为 "1"，Release 包与历史包为 null。
    // E2E 运行器按 gitCommit + tpE2EBuild 两个字段找可复用的包：只有 E2E 构建的包才带沙盒覆盖文件加载器。
    @Column(length = 16)
    private String tpE2EBuild;
    // 文件名
    private String fileName;
    // 是否是 定版包
    private boolean isRelease;
    // 打出该包的 Jenkins 节点名（Jenkins-01 / Jenkins-02），仅 iOS 打包 Job 上传时带；用于找回 .xcarchive
    @Column(length = 32)
    private String archiveNode;
    // 该包对应 .xcarchive 在打包机上的绝对路径（文件可能已被打包机清理，以实时探测为准）
    @Column(length = 512)
    private String archivePath;
    // 上传 App Store Connect 的状态：null=未上传 / UPLOADING / UPLOADED / FAILED
    @Column(length = 16)
    private String ascStatus;
    // 上传时 Xcode 自动递增后实际使用的 build 号（取不到时为 null）
    @Column(length = 32)
    private String ascBuildVersion;
    // 最近一次上传结束（成功或失败）的时间，毫秒；用包装类型，历史行加列后为 NULL
    private Long ascUploadTime;
    // 最近一次上传的结果摘要（失败原因等），完整日志在包目录 asc_upload.log
    @Column(length = 1024)
    private String ascMessage;

    @ManyToOne(cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @JoinColumn(name="appId")
    private App app;
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    // Provision 文件
    @JoinColumn(name = "provisionId",referencedColumnName = "id")
    private  Provision provision;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBundleID() {
        return bundleID;
    }

    public void setBundleID(String bundleID) {
        this.bundleID = bundleID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public void setBuildVersion(String buildVersion) {
        this.buildVersion = buildVersion;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getMinVersion() {
        return minVersion;
    }

    public void setMinVersion(String minVersion) {
        this.minVersion = minVersion;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public String getGitCommit() {
        return gitCommit;
    }

    public void setGitCommit(String gitCommit) {
        this.gitCommit = gitCommit;
    }

    public String getTpE2EBuild() {
        return tpE2EBuild;
    }

    public void setTpE2EBuild(String tpE2EBuild) {
        this.tpE2EBuild = tpE2EBuild;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public App getApp() {
        return app;
    }

    public void setApp(App app) {
        this.app = app;
    }

    public Provision getProvision() {
        return provision;
    }

    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    public boolean getIsRelease() {
        return isRelease;
    }

    public void setIsRelease(boolean isRelease) {
        this.isRelease = isRelease;
    }

    public String getArchiveNode() {
        return archiveNode;
    }

    public void setArchiveNode(String archiveNode) {
        this.archiveNode = archiveNode;
    }

    public String getArchivePath() {
        return archivePath;
    }

    public void setArchivePath(String archivePath) {
        this.archivePath = archivePath;
    }

    public String getAscStatus() {
        return ascStatus;
    }

    public void setAscStatus(String ascStatus) {
        this.ascStatus = ascStatus;
    }

    public String getAscBuildVersion() {
        return ascBuildVersion;
    }

    public void setAscBuildVersion(String ascBuildVersion) {
        this.ascBuildVersion = ascBuildVersion;
    }

    public Long getAscUploadTime() {
        return ascUploadTime;
    }

    public void setAscUploadTime(Long ascUploadTime) {
        this.ascUploadTime = ascUploadTime;
    }

    public String getAscMessage() {
        return ascMessage;
    }

    public void setAscMessage(String ascMessage) {
        this.ascMessage = ascMessage;
    }
}
