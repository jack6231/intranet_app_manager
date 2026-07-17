---
name: intranet-app-manager
description: "Build, release, and operate the intranet_app_manager app-distribution platform (Spring Boot, Java) deployed on Jenkins Node2 (iospub@10.2.54.235, http://10.2.54.235:8082/apps). Use when the user wants to publish/deploy a new version, rebuild the jar, check/restart/stop the service, view its logs, or roll back. Handles the JDK-11 build workaround, jar slimming, the version-pinned start script, launchd KeepAlive restart semantics, and protection of the 204GB uploads plus external config. Triggers include 发布 app 管理平台, 部署 intranet_app_manager, 更新托管平台, release app manager, 重启/停/看日志 app 托管, 回滚 app manager, or shipping work inside the intranet_app_manager repo."
---

# Intranet App Manager — 构建 / 发布 / 运维

维护 TruckerPath 内网 APP 分发平台 `intranet_app_manager`:本地构建 jar → 发布到 Node2(235)→ 服务运维与回滚。

> 首次接手或涉及环境细节时,先读 `references/environment.md`(项目、构建 JDK 坑、235 部署形态、外置数据、红线)。git 分支/发版操作走全局 `/git` skill。

## 五条红线(任何操作前记住)

1. 发布只替换 jar 并备份;**绝不覆盖/删除** 235 上的 `config/`、`static/`(204GB 上传数据)、`server.pkcs12`。
2. jar 文件名带版本号且被 `start_appmanager.sh` 引用;版本变化必须同步改启动脚本(脚本已自动处理)。
3. 重启用 `kill`(launchd KeepAlive 自动拉起,免 sudo);**绝不 `nohup java -jar`**(会双开抢端口)。
4. 真正停服才需 sudo `launchctl bootout`;KeepAlive 下单纯 kill 停不掉。
5. 本地构建必须用 JDK 11(`gradle.properties` 已配);正常 jar ≈ 44MB,几百 MB 说明 `static/upload` 排除失效。

## 意图路由

| 用户意图 | 操作 |
|---------|------|
| 发布 / 部署 / 更新新版本 | `scripts/deploy.sh`(见下)|
| 只在本地出包 | 仓库根跑 `./gradlew clean bootJar`(JDK 11 已由 gradle.properties 指定)|
| 查状态 / 重启 / 停 / 看日志 / 回滚 | `scripts/service.sh <status\|restart\|stop\|start\|logs\|rollback>` |

## 发布流程

发布会**短暂重启线上服务**(对外可见)。执行 `deploy.sh` 前先与用户确认发布哪个版本。

1. **(可选)先走 git 发版**:正式发布时,先用 `/git` skill 把 `develop` 合入 `release` 并打 tag `vX.Y.Z`(版本对齐 `build.gradle` 的 `version`)。
2. **构建 + 发布**:
   ```bash
   bash scripts/deploy.sh --build           # 本地构建后发布最新 jar
   bash scripts/deploy.sh                    # 发布 build/libs 中最新的 jar(不重新构建)
   bash scripts/deploy.sh /path/to/xxx.jar   # 发布指定 jar
   ```
   `deploy.sh` 自动完成:校验 jar(含 BOOT-INF、非虚胖、无 static/upload)→ scp(临时名再落定)→ 备份旧 jar(留最近 3 个)→ 版本变化时 `sed` 改 `start_appmanager.sh` → kill 触发 launchd 重启 → 轮询 `http://localhost:8082/apps` 至 200。
3. **失败处理**:健康检查不过时脚本会提示看 `launchd.log` 并 `service.sh rollback`。

## 运维

```bash
bash scripts/service.sh status     # 进程/端口/健康码/当前 jar/launchd 状态
bash scripts/service.sh restart    # kill + 自动拉起 + 健康检查
bash scripts/service.sh logs 200   # tail launchd.log 末 200 行
bash scripts/service.sh rollback   # 回滚到 backups/ 最近一个 jar
bash scripts/service.sh stop       # 打印 sudo bootout 停服指引
```

## 环境速查(详见 references/environment.md)

- 部署机:`ssh iospub@10.2.54.235`(免密),目录 `/Users/iospub/intranet_app_manager/`
- 自启:LaunchDaemon `com.truckerpath.appmanager` → `start_appmanager.sh`(等 MySQL 再 `java -jar`)
- 依赖:MySQL(库 `app_manager`,独立 LaunchDaemon 守护,ddl-auto=update)
- 本 skill 随项目版本化,位于 `<repo>/.claude/skills/intranet-app-manager/`;脚本从自身位置推导仓库根(可用 `IAM_REPO` 覆盖)
- 关系:与 `jenkins` skill 的 Node2 同机;本 skill 取代其 app-hosting 文档里过时的 `nohup` 启停方式,改用 launchd。
