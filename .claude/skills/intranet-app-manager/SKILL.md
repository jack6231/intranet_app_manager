---
name: intranet-app-manager
description: "Build, release, and operate the intranet_app_manager app-distribution platform (Spring Boot, Java) deployed on Jenkins Node2 (iospub@10.2.54.235, http://10.2.54.235:8082/apps). Use when the user wants to publish/deploy a new version, rebuild the jar, check/restart/stop the service, view its logs, or roll back. Handles the JDK-11 build workaround, jar slimming, the version-pinned start script, launchd KeepAlive restart semantics, and protection of the 204GB uploads plus external config. Triggers include 发布 app 管理平台, 部署 intranet_app_manager, 更新托管平台, release app manager, 重启/停/看日志 app 托管, 回滚 app manager, or shipping work inside the intranet_app_manager repo."
---

# Intranet App Manager — 构建 / 发布 / 运维

维护 TruckerPath 内网 APP 分发平台 `intranet_app_manager`:本地构建 jar → 发布到 Node2(235)→ 服务运维与回滚。

> 首次接手或涉及环境细节时,先读 `references/environment.md`(项目、构建 JDK 坑、235 部署形态、外置数据、红线)。git 分支/发版操作走全局 `/git` skill。

## 红线(任何操作前记住)

1. **先测服 236 验证,再正服 235**;`deploy.sh`/`service.sh` 默认 `test`,发正服必须显式 `prod` 且经用户确认。
2. 发布只替换 jar 并备份;**绝不覆盖/删除** 远端的 `config/`、`static/`(正服 204GB 上传数据)、`server.pkcs12`。
3. jar 文件名带版本号且被 `start_appmanager.sh` 引用;版本变化必须同步改启动脚本(脚本已自动处理)。
4. **重启方式按环境**:正服 kill 后靠 launchd KeepAlive 自动拉起(**绝不 `nohup java -jar`**,会双开);测服无 launchd,kill 后需 nohup 重起。`deploy.sh`/`service.sh` 已自动区分。
5. 正服停服才需 sudo `launchctl bootout`;KeepAlive 下单纯 kill 停不掉。
6. 本地构建必须用 JDK 11(`gradle.properties` 已配);正常 jar ≈ 44MB,几百 MB 说明 `static/upload` 排除失效。

## 意图路由

| 用户意图 | 操作 |
|---------|------|
| 搭建/清理测服(236)| `scripts/testenv-setup.sh` / `scripts/testenv-teardown.sh`(用时现搭、用完清干净)|
| 发布 / 部署 / 更新新版本 | `scripts/deploy.sh [test\|prod]`(见下)|
| 只在本地出包 | 仓库根跑 `./gradlew clean bootJar`(JDK 11 已由 gradle.properties 指定)|
| 查状态 / 重启 / 停 / 看日志 / 回滚 | `scripts/service.sh [test\|prod] <status\|restart\|stop\|start\|logs\|rollback>` |

## 发布流程(先测服 236 → 再正服 235)

发布会**短暂重启服务**(正服对外可见)。执行前先与用户确认发布哪个版本、哪个环境。

1. **先在测服验证**(测服 = 用时现搭、用完清干净,不常驻):
   ```bash
   bash scripts/testenv-setup.sh             # 从零搭建 236 并部署(建库/config/证书/jar/nohup 启动)
   # 造数据:把 scripts/testdata/mock.sh + 图标 scp 到 236 后 ssh 执行(见脚本头部)
   # 到 http://10.2.54.236:8082/apps 验证功能
   bash scripts/testenv-teardown.sh          # 测完彻底清理(停 app/删库/删目录)
   ```
   同一轮换新 jar 重发用 `bash scripts/deploy.sh test --build`(目录已在时)。
2. **测通后走 git 发版**(正式发布):用 `/git` skill 把 `develop` 合入 `release` 并打 tag `vX.Y.Z`(对齐 `build.gradle` 的 `version`)。
3. **发正服(需显式 prod + 用户确认)**:
   ```bash
   bash scripts/deploy.sh prod --build       # 发到 235 正服;prod 必须显式写,防误发
   ```
   `deploy.sh` 自动:校验 jar(含 BOOT-INF、非虚胖、无 static/upload)→ scp(临时名再落定)→ 备份旧 jar(留最近 3 个)→ 版本变化时 `sed` 改 `start_appmanager.sh` → 按环境重启(prod=kill 靠 launchd / test=kill 后 nohup)→ 轮询 `/apps` 至 200。
4. **失败处理**:健康检查不过时脚本提示看 `launchd.log`,用 `service.sh <env> rollback` 秒回上一个 jar。

## 运维

```bash
bash scripts/service.sh prod status     # 正服:进程/端口/健康码/当前 jar/launchd 状态
bash scripts/service.sh test restart    # 测服:kill + nohup 重起 + 健康检查
bash scripts/service.sh prod logs 200   # tail launchd.log 末 200 行
bash scripts/service.sh prod rollback   # 回滚到 backups/ 最近一个 jar
bash scripts/service.sh prod stop       # 正服停服指引(需 sudo bootout);test 则直接 kill
```
> 环境省略默认 `test`。正服操作务必显式带 `prod`。

## 环境速查(详见 references/environment.md)

- 部署机:`ssh iospub@10.2.54.235`(免密),目录 `/Users/iospub/intranet_app_manager/`
- 自启:LaunchDaemon `com.truckerpath.appmanager` → `start_appmanager.sh`(等 MySQL 再 `java -jar`)
- 依赖:MySQL(库 `app_manager`,独立 LaunchDaemon 守护,ddl-auto=update)
- 本 skill 随项目版本化,位于 `<repo>/.claude/skills/intranet-app-manager/`;脚本从自身位置推导仓库根(可用 `IAM_REPO` 覆盖)
- 关系:与 `jenkins` skill 的 Node2 同机;本 skill 取代其 app-hosting 文档里过时的 `nohup` 启停方式,改用 launchd。
