# intranet_app_manager — 项目与部署环境参考

## 项目

- **性质**:Spring Boot 2.1.6(Java 8 源码)企业内网 APP 分发平台(类 fir.im/蒲公英),上传 iOS `ipa` / Android `apk`·`aab`,生成安装页、二维码、iOS OTA `manifest.plist`,钉钉 WebHook。
- **本地仓库(开发机)**:`/Users/moatable/Workspace/intranet_app_manager`
- **远程 git**:`git@github.com:jack6231/intranet_app_manager.git`
- **分支模型**:`develop`=开发主线 · `release`=生产分支(打 tag `vX.Y.Z`) · `master`=原作者旧线(停维护)。git 操作走全局 `/git` skill。

## 构建(关键:JDK 版本坑)

- Gradle wrapper 锁 **5.4.1**,仅支持 **Java 8–12**;开发机默认 java 是 **21**(Android Studio JBR)→ 直接 `./gradlew` 会报 `Unsupported class file major version`。
- 解决:已装 `openjdk@11`(brew formula,keg-only),`gradle.properties` 里 `org.gradle.java.home=/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home` 让构建守护进程用 11(客户端仍 21,仅负责拉起,不影响结果)。
- 出包命令:`./gradlew clean bootJar` → 产物 `build/libs/intranet_app_manager-<version>.jar`。版本号在 `build.gradle` 的 `version`(当前 1.0.7)。
- **jar 瘦身**:`build.gradle` 的 `processResources { exclude 'static/upload/**' }` 已排除开发期上传包。正常 jar ≈ **44MB**;若见到几百 MB,说明排除失效(历史上曾达 744MB,含 676MB 的 `static/upload` 死重)。

## 两套环境:测服 236 / 正服 235

发布纪律:**先测服 236 验证 → 再正服 235**。`deploy.sh`/`service.sh` 用 `test|prod` 参数区分,默认 `test`,正服必须显式 `prod`,防误发。

| | 测服(test) | 正服(prod) |
|--|-----------|-----------|
| 主机 | `iospub@10.2.54.236`(Node3 自动化测试机) | `iospub@10.2.54.235`(Node2) |
| 域名 | `server.domain=10.2.54.236` | `server.domain=10.2.54.235` |
| 对外 | http://10.2.54.236:8082/apps | http://10.2.54.235:8082/apps |
| MySQL | 9.7.x(brew,root 空密码) | 9.6.x |
| 运行方式 | **nohup**(暂无 launchd 持久化,缺 sudo);重启=kill 后 nohup 重起 | **launchd** `com.truckerpath.appmanager`(KeepAlive);重启=kill 靠自动拉起 |
| 数据 | 造的 mock 数据(见 `scripts/testdata/mock.sh`) | 真实数据,勿动 |

> 关键差异:**测服重启必须 nohup 重起**(kill 后不会自动回来);正服 kill 后 launchd 会拉起。`deploy.sh`/`service.sh` 已按环境自动处理。
> 待办:236 若配上 launchd(需 sudo 一次装 plist),即可与正服行为完全一致,届时把测服 RESTART_MODE 改为 launchd。

### 造测试数据(测服)

`scripts/testdata/mock.sh` 在 236 上生成 3 个 App(iOS Dev/Android/Legacy),含不同新旧+定版+当前包组合。用法见脚本头部(scp 图标+脚本到 236 后 ssh 执行)。**切勿在正服跑。**

## 部署目标机 Node2(235,正服)

- **SSH**:`ssh iospub@10.2.54.235`(已配公钥,免密)。同 jenkins skill 的 Node2。
- **目录**:`/Users/iospub/intranet_app_manager/`(**不是 git 仓库,是 jar 投放目录**)
- **对外地址**:`http://10.2.54.235:8082/apps`(http=8082,https=8081)
- **JDK**:Temurin 17(`/Library/Java/JavaVirtualMachines/temurin-17.jdk`,`java -jar` 运行 OK)
- **MySQL**:homebrew,库 `app_manager`,独立 LaunchDaemon `com.truckerpath.mysql` 守护;Hibernate `ddl-auto=update` 自动建表。

### 运行 / 自启(launchd)

- 系统级 LaunchDaemon **`com.truckerpath.appmanager`**(`/Library/LaunchDaemons/`,root 属主,`UserName=iospub`):
  - `RunAtLoad=true` + `KeepAlive=true` → 开机自启、**进程退出自动拉起**。
  - 执行 `ProgramArguments = start_appmanager.sh`,`WorkingDirectory = 部署目录`,日志 → `launchd.log`。
- `start_appmanager.sh`:等 MySQL:3306 就绪,再 `exec .../temurin-17/bin/java -jar .../intranet_app_manager-<version>.jar`。

### 外置且必须保留的东西(部署时绝不覆盖/删除)

- `config/application.properties` — **覆盖 jar 内置配置**:`config.debug=release`(静态资源走文件系统 `static/` 而非 classpath)、`server.domain=10.2.54.235`、上传上限 800MB、外置 `server.pkcs12`、MySQL 本地 root 空密码。
- `static/` — 含 **`static/upload` ≈ 204GB 历史上传包**;`ca.crt`/`crt/`。盘用量约 80%。
- `server.pkcs12`(+ `.bak`)— 自签 HTTPS 证书,密码 `123456`。

## 关键坑(部署/运维必须遵守)

1. **jar 文件名版本锁死**:`start_appmanager.sh` 里写死 `intranet_app_manager-<version>.jar`。版本一变必须同步改启动脚本(`deploy.sh` 已自动 `sed` 处理并备份)。plist 只调脚本、不直接引用 jar,故改脚本即可,无需动 plist。
2. **重启用 kill,不用 nohup**:有 launchd KeepAlive,`kill` 进程即被自动拉起(免 sudo)。**切勿再 `nohup java -jar`**,会双开抢端口。(jenkins skill 旧文档里的 nohup 方式已过时。)
3. **真正停服需 sudo**:仅 kill 会被 KeepAlive 立即拉起;停服要 `sudo launchctl bootout system/com.truckerpath.appmanager`。
4. **数据保护**:发布只替换 jar(并备份),绝不碰 `config/`、`static/`、`server.pkcs12`。
5. **健康检查**:重启后轮询 `curl http://localhost:8082/apps` 直到 `200`(需等 MySQL + Spring Boot 启动,约数十秒)。
