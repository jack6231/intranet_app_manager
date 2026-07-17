#!/bin/bash
# 发布 intranet_app_manager 到 Node2(235)。
# 流程:(可选)本地构建 → 校验 jar → scp 到 235 → 备份旧 jar → 必要时同步启动脚本里的 jar 名
#        → 重启(kill 进程,launchd KeepAlive 自动拉起)→ 健康检查。
#
# 绝不触碰远端的 config/、static/(204G 上传数据)、server.pkcs12。
#
# 用法:
#   deploy.sh [--build] [jar路径]
#     --build    先在本地跑 ./gradlew clean bootJar
#     jar路径    指定要发布的 jar;缺省取 <repo>/build/libs 中最新的 intranet_app_manager-*.jar
#
# 可用环境变量覆盖:
#   IAM_REPO   本地仓库路径(默认从脚本位置推导:skill 位于 <repo>/.claude/skills/intranet-app-manager/scripts)
set -euo pipefail

HOST="iospub@10.2.54.235"
REMOTE_DIR="/Users/iospub/intranet_app_manager"
START_SCRIPT="start_appmanager.sh"
HEALTH_PATH="/apps"
HTTP_PORT="8082"
PROC_PATTERN="intranet_app_manager-.*\.jar"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IAM_REPO="${IAM_REPO:-$(cd "$SCRIPT_DIR/../../../.." && pwd)}"
[ -f "$IAM_REPO/build.gradle" ] || { echo "[deploy:ERROR] 未找到仓库根($IAM_REPO 无 build.gradle),请用 IAM_REPO 指定" >&2; exit 1; }

DO_BUILD=0
JAR_ARG=""
for a in "$@"; do
  case "$a" in
    --build) DO_BUILD=1 ;;
    *) JAR_ARG="$a" ;;
  esac
done

log() { printf '\033[1;34m[deploy]\033[0m %s\n' "$*"; }
err() { printf '\033[1;31m[deploy:ERROR]\033[0m %s\n' "$*" >&2; }

# 1) 可选构建 ---------------------------------------------------------------
if [ "$DO_BUILD" -eq 1 ]; then
  log "本地构建:./gradlew clean bootJar (JDK 由 gradle.properties 指定为 11)"
  ( cd "$IAM_REPO" && ./gradlew clean bootJar )
fi

# 2) 选定 jar --------------------------------------------------------------
if [ -n "$JAR_ARG" ]; then
  JAR="$JAR_ARG"
else
  JAR="$(ls -t "$IAM_REPO"/build/libs/intranet_app_manager-*.jar 2>/dev/null | head -1 || true)"
fi
[ -n "$JAR" ] && [ -f "$JAR" ] || { err "找不到要发布的 jar,请先 --build 或显式传入路径"; exit 1; }
JAR_NAME="$(basename "$JAR")"
JAR_SIZE_MB=$(( $(stat -f%z "$JAR") / 1048576 ))
log "待发布 jar: $JAR_NAME (${JAR_SIZE_MB}MB)"

# 3) 校验 jar --------------------------------------------------------------
unzip -l "$JAR" 2>/dev/null | grep -q "BOOT-INF/" || { err "$JAR_NAME 不是 Spring Boot 可执行 jar(缺 BOOT-INF)"; exit 1; }
if unzip -l "$JAR" 2>/dev/null | grep -qi "static/upload/"; then
  err "jar 里打进了 static/upload —— 构建未排除,体积会虚胖。检查 build.gradle 的 processResources exclude。"
  exit 1
fi
if [ "$JAR_SIZE_MB" -gt 200 ]; then
  err "jar 体积异常(${JAR_SIZE_MB}MB > 200MB),疑似打进了不该有的资源,已中止。"
  exit 1
fi

# 4) 传输(先传临时名,成功后再改名,避免半包覆盖运行中文件)---------------
log "scp → $HOST:$REMOTE_DIR/$JAR_NAME.uploading"
scp -q "$JAR" "$HOST:$REMOTE_DIR/$JAR_NAME.uploading"

# 5) 远端:备份旧 jar → 落定新 jar → 同步启动脚本 → 重启 → 健康检查 --------
log "远端:备份 / 落定 / 同步启动脚本 / 重启 / 健康检查"
ssh "$HOST" JAR_NAME="$JAR_NAME" REMOTE_DIR="$REMOTE_DIR" START_SCRIPT="$START_SCRIPT" \
    HEALTH_PATH="$HEALTH_PATH" HTTP_PORT="$HTTP_PORT" PROC_PATTERN="$PROC_PATTERN" 'bash -s' <<'REMOTE'
set -euo pipefail
cd "$REMOTE_DIR"

# 当前启动脚本引用的 jar 名(版本锁死点)
CUR_REF=$(grep -oE 'intranet_app_manager-[^ ]*\.jar' "$START_SCRIPT" | head -1 || true)
echo "  启动脚本当前引用: ${CUR_REF:-<无>}"

# 备份当前引用的 jar(保留最近 3 个)
mkdir -p backups
if [ -n "$CUR_REF" ] && [ -f "$CUR_REF" ] && [ "$CUR_REF" != "$JAR_NAME" ]; then
  cp -p "$CUR_REF" "backups/${CUR_REF}.$(date +%Y%m%d%H%M%S)"
elif [ -f "$JAR_NAME" ]; then
  cp -p "$JAR_NAME" "backups/${JAR_NAME}.$(date +%Y%m%d%H%M%S)"
fi
ls -1t backups/ | tail -n +4 | while read -r old; do rm -f "backups/$old"; done

# 落定新 jar
mv -f "${JAR_NAME}.uploading" "$JAR_NAME"
echo "  已落定: $JAR_NAME"

# 同步启动脚本里的 jar 名(版本变化时)
if [ -n "$CUR_REF" ] && [ "$CUR_REF" != "$JAR_NAME" ]; then
  cp -p "$START_SCRIPT" "backups/${START_SCRIPT}.$(date +%Y%m%d%H%M%S)"
  sed -i '' "s|$CUR_REF|$JAR_NAME|g" "$START_SCRIPT"
  echo "  启动脚本已更新: $CUR_REF -> $JAR_NAME"
fi

# 重启:kill 运行中的 java,launchd KeepAlive 会用(更新后的)启动脚本自动拉起
PID=$(pgrep -f "$PROC_PATTERN" || true)
if [ -n "$PID" ]; then
  echo "  kill 旧进程 PID=$PID(launchd 将自动重启)"
  kill "$PID"
else
  echo "  未发现运行中进程(launchd 应会按 RunAtLoad 拉起)"
fi

# 健康检查:等 MySQL + 应用启动,最多 ~90s
echo -n "  健康检查 http://localhost:$HTTP_PORT$HEALTH_PATH "
CODE=000
for i in $(seq 1 45); do
  sleep 2
  CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$HTTP_PORT$HEALTH_PATH" || echo 000)
  [ "$CODE" = "200" ] && break
  printf '.'
done
echo " -> $CODE"
if [ "$CODE" != "200" ]; then
  echo "  ❌ 健康检查未通过。查看日志: tail -100 $REMOTE_DIR/launchd.log"
  echo "  回滚: service.sh rollback"
  exit 1
fi
NEWPID=$(pgrep -f "$PROC_PATTERN" || true)
echo "  ✅ 发布成功,运行 jar=$JAR_NAME PID=$NEWPID"
REMOTE

log "完成。对外地址: http://10.2.54.235:8082/apps"
