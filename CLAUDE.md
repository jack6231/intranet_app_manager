# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

企业内网 APP 分发平台（类似 fir.im / 蒲公英），供 TruckerPath 内部使用。上传 iOS `ipa` 与 Android `apk`/`aab` 包，自动解析元信息、生成安装页与二维码、生成 iOS OTA 安装用的 `manifest.plist`，并支持钉钉 WebHook 通知与 Jenkins 集成上传。

技术栈：Spring Boot 2.1.6（Java 8）、Thymeleaf、Spring Data JPA + Hibernate、MySQL。构建版本见 `build.gradle`（当前 1.0.7）。

## 常用命令

```bash
./gradlew build              # 编译打包，产物在 build/libs/intranet_app_manager-<version>.jar
./gradlew bootRun            # 本地启动（读 src/main/resources/application.properties）
./gradlew test               # 运行测试
./gradlew test --tests org.yzr.main.ApplicationTests   # 单个测试类
java -jar build/libs/intranet_app_manager-<version>.jar  # 部署运行
```

运行前提：本地 MySQL 已启动，且存在库 `app_manager`（`create database app_manager DEFAULT CHARSET utf8;`）。表结构由 Hibernate `ddl-auto=update` 自动创建/更新，无需手写 SQL 迁移。

## 运行时关键配置（application.properties）

- **双连接器**：HTTPS 在 `server.port`（默认 8081），HTTP 在 `server.http.port`（默认 8082）。`Application.java` 手动注册 HTTP Connector，两个端口同时监听。默认走 HTTP 以避免证书信任；**iOS OTA 安装必须走 HTTPS**（自签名 pkcs12 证书，密码 `123456`，部署时需自行替换 `server.pkcs12` 和 `static/crt/ca.crt`）。
- **`server.domain`**：部署机的 IP 或域名，用于拼接对外 URL。
- **`config.debug=debug`**：开发模式开关。debug 时静态/上传资源从 classpath 读取；非 debug 时从 jar 所在目录的 `static/` 读取（见 `WebAppConfigurer`）。生产部署需关掉或改此值，否则上传的包无法正确定位。
- 上传大小上限 500MB。

⚠️ `PathManager.getBaseURL()` 里硬编码了部署相关的特判（`apphost.truckerpath.com` 去端口、`34.221.237.191` 用 host 覆盖 domain）。改动 URL 生成逻辑时注意这些环境特例。

## 架构

分层：`Controller → Service → DAO(Spring Data JPA) → MySQL`。实体（`model`）不直接进模板，统一包成 `vo`（ViewModel）供 Thymeleaf 使用，ViewModel 构造时接收 `PathManager` 和 `HttpServletRequest` 以按当前请求协议（http/https）拼接资源 URL。

### 领域模型（org.yzr.model）

- **App**：一个应用，唯一约束 `(platform, bundleID)`。持有 `packageList`（历史版本）、`webHookList`、`currentPackage`（当前展示版本），以及 4 位 `shortCode`（短链用）。
- **Package**：单个上传包（一个版本）。含平台、版本、构建号、大小、`isRelease`（是否定版）、`extra`（JSON，存 Jenkins jobName/buildNumber）、可选 `provision`（iOS 描述文件）。
- **WebHook** / **Provision**：通知配置与 iOS provision。

上传去重逻辑在 `AppService.getByPackage()`：按 `(bundleID, platform)` 查已有 App，命中则追加新 Package，否则新建 App 并生成不重复的 shortCode。

### 两处反射式插件分发（扩展时按命名约定加类即可，无需改调度代码）

- **包解析器** `ParserClient`：按文件扩展名反射加载 `org.yzr.utils.parser.{EXT大写}Parser`（现有 `IPAParser`/`APKParser`/`AABParser`，均实现 `PackageParser`）。新增格式 → 新增一个 `{EXT}Parser` 实现 `PackageParser`。
- **WebHook** `WebHookClient`：按类型反射加载 `org.yzr.utils.webhook.{Type}WebHook`（现有 `DingDingWebHook` 实现 `IWebHook`），实例缓存在静态 Map。新增通知渠道 → 新增 `{Type}WebHook` 实现 `IWebHook`。

### 文件存储与静态映射

上传包落盘到 `static/upload/{platform}/{bundleID}/{createTime}/`，目录内含重命名后的包文件（`{platform}.{ext}`）与 `icon.png`。所有路径/URL 拼接集中在 **`PathManager`**——涉及文件位置或对外链接时改这里，不要在 Controller/Service 里手拼。`WebAppConfigurer` 把 `/android/**`、`/ios/**` 映射到对应上传目录，`/crt/**` 映射到证书目录。

### 主要路由（Controller）

| 路由 | 说明 |
|------|------|
| `/apps`、`/apps/{id}` | 应用列表页 / 某应用的版本列表页 |
| `/app/upload` | 上传包（`multipart file`，可带 `jobName`/`buildNumber`），返回二维码 URL |
| `/s/{code}` | 短链安装页（`install` 模板） |
| `/p/{id}` | 下载原始包 |
| `/m/{id}` | 生成 iOS `manifest.plist`（`PlistGenerator` + FreeMarker 模板 `manifest.plist`，itms-services OTA） |
| `/p/code/{id}` | 生成包二维码 PNG（zxing） |
| `/p/update/{id}/{isRelease}`、`*/delete/*` | 定版 / 删除 |

### Jenkins 集成

CI 上传脚本 `curl -F "file=@..." http://<host>/app/upload`，从返回 JSON 提取二维码 URL 注入 Jenkins 变量并在构建页展示二维码（详见 README「Jenkins 集成」）。

## 惯例

- 代码内注释与文档为中文；实体/服务遵循经典 Spring 分层，Service 方法多为 `@Transactional`。
- 关键扩展点靠反射 + 命名约定，改动时优先遵循约定而非改调度器。
