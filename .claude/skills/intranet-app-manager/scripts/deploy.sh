#!/bin/bash
# 发布 intranet_app_manager 到测服(236)或正服(235)。
# 流程:(可选)本地构建 → 校验 jar → scp → 备份旧 jar → 必要时同步启动脚本里的 jar 名
#        → 重启(prod: kill 靠 launchd KeepAlive 拉起 / test: kill 后 nohup 重起)→ 健康检查。
#
# 绝不触碰远端的 config/、static/(上传数据)、server.pkcs12。
#
# 用法:
#   deploy.sh [test|prod] [--build] [jar路径]
#     test       目标 236 测服(默认);正服必须显式写 prod,防误发
#     prod       目标 235 正服
#     --build    先在本地跑 ./gradlew clean bootJar
#     jar路径    指定要发布的 jar;缺省取 <repo>/build/libs 中最新的 intranet_app_manager-*.jar
#
# 环境变量:
#   IAM_REPO   本地仓库路径(默认从脚本位置推导:skill 在 <repo>/.claude/skills/intranet-app-manager/scripts)
set -euo pipefail

REMOTE_DIR="/Users/iospub/intranet_app_manager"
START_SCRIPT="start_appmanager.sh"
HEALTH_PATH="/apps"
HTTP_PORT="8082"
PROC_PATTERN="intranet_app_manager-.*\.jar"

ENV=""; DO_BUILD=0; JAR_ARG=""
for a in "$@"; do
  case "$a" in
    test|prod) ENV="$a" ;;
    --build)   DO_BUILD=1 ;;
    *)         JAR_ARG="$a" ;;
  esac
done
ENV="${ENV:-test}"
case "$ENV" in
  test) HOST="iospub@10.2.54.236"; RESTART_MODE="nohup" ;;
  prod) HOST="iospub@10.2.54.235"; RESTART_MODE="launchd" ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IAM_REPO="${IAM_REPO:-$(cd "$SCRIPT_DIR/../../../.." && pwd)}"
[ -f "$IAM_REPO/build.gradle" ] || { echo "[deploy:ERROR] 未找到仓库根($IAM_REPO 无 build.gradle),请用 IAM_REPO 指定" >&2; exit 1; }

log() { printf '\033[1;34m[deploy:%s]\033[0m %s\n' "$ENV" "$*"; }
err() { printf '\033[1;31m[deploy:%s:ERROR]\033[0m %s\n' "$ENV" "$*" >&2; }

log "目标:$HOST(重启方式=$RESTART_MODE)"

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
# 注意:用 grep -c 读完整输入,不用 grep -q。pipefail 下 grep -q 命中即关管道,
# 会让 unzip 收 SIGPIPE(退出 141)导致整条管道被误判为失败。
JAR_ENTRIES="$(unzip -l "$JAR" 2>/dev/null || true)"
if [ "$(printf '%s\n' "$JAR_ENTRIES" | grep -c 'BOOT-INF/')" -eq 0 ]; then
  err "$JAR_NAME 不是 Spring Boot 可执行 jar(缺 BOOT-INF)"; exit 1
fi
if [ "$(printf '%s\n' "$JAR_ENTRIES" | grep -ci 'static/upload/')" -gt 0 ]; then
  err "jar 里打进了 static/upload —— 构建未排除,体积会虚胖。检查 build.gradle 的 processResources exclude。"
  exit 1
fi

# 4) 传输(先传临时名,成功后再改名,避免半包覆盖运行中文件)---------------
log "scp → $HOST:$REMOTE_DIR/$JAR_NAME.uploading"
scp -q "$JAR" "$HOST:$REMOTE_DIR/$JAR_NAME.uploading"

# 5) 远端:备份旧 jar → 落定新 jar → 同步启动脚本 → 重启 → 健康检查 --------
log "远端:备份 / 落定 / 同步启动脚本 / 重启($RESTART_MODE)/ 健康检查"
ssh "$HOST" JAR_NAME="$JAR_NAME" REMOTE_DIR="$REMOTE_DIR" START_SCRIPT="$START_SCRIPT" \
    HEALTH_PATH="$HEALTH_PATH" HTTP_PORT="$HTTP_PORT" PROC_PATTERN="$PROC_PATTERN" \
    RESTART_MODE="$RESTART_MODE" 'bash -s' <<'REMOTE'
set -euo pipefail
cd "$REMOTE_DIR"

CUR_REF=$(grep -oE 'intranet_app_manager-[^ ]*\.jar' "$START_SCRIPT" | head -1 || true)
echo "  启动脚本当前引用: ${CUR_REF:-<无>}"

mkdir -p backups
if [ -n "$CUR_REF" ] && [ -f "$CUR_REF" ] && [ "$CUR_REF" != "$JAR_NAME" ]; then
  cp -p "$CUR_REF" "backups/${CUR_REF}.$(date +%Y%m%d%H%M%S)"
elif [ -f "$JAR_NAME" ]; then
  cp -p "$JAR_NAME" "backups/${JAR_NAME}.$(date +%Y%m%d%H%M%S)"
fi
ls -1t backups/ | tail -n +4 | while read -r old; do rm -f "backups/$old"; done

mv -f "${JAR_NAME}.uploading" "$JAR_NAME"
echo "  已落定: $JAR_NAME"

if [ -n "$CUR_REF" ] && [ "$CUR_REF" != "$JAR_NAME" ]; then
  cp -p "$START_SCRIPT" "backups/${START_SCRIPT}.$(date +%Y%m%d%H%M%S)"
  sed -i '' "s|$CUR_REF|$JAR_NAME|g" "$START_SCRIPT"
  echo "  启动脚本已更新: $CUR_REF -> $JAR_NAME"
fi

# 重启
PID=$(pgrep -f "$PROC_PATTERN" || true)
[ -n "$PID" ] && { echo "  kill 旧进程 PID=$PID"; kill "$PID"; } || echo "  未发现运行中进程"
if [ "$RESTART_MODE" = "nohup" ]; then
  # 测服:等旧进程退出后 nohup 重起(无 launchd)
  for i in 1 2 3 4 5; do pgrep -f "$PROC_PATTERN" >/dev/null || break; sleep 1; done
  pkill -9 -f "$PROC_PATTERN" 2>/dev/null || true
  nohup ./"$START_SCRIPT" > launchd.log 2>&1 </dev/null &
  echo "  nohup 重启"
else
  echo "  launchd KeepAlive 将自动拉起"
fi

# 健康检查
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
  echo "  回滚: service.sh <test|prod> rollback"
  exit 1
fi
echo "  ✅ 发布成功,运行 jar=$JAR_NAME PID=$(pgrep -f "$PROC_PATTERN" | head -1)"
REMOTE

DOMAIN=$([ "$ENV" = prod ] && echo "10.2.54.235" || echo "10.2.54.236")
log "完成。对外地址: http://$DOMAIN:8082/apps"
