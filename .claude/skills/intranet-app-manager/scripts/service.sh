#!/bin/bash
# intranet_app_manager 在 Node2(235)上的服务运维。
#
# 用法: service.sh <status|restart|stop|start|logs|rollback>
#
#   status    进程 / 端口 / 健康码 / launchd 状态
#   restart   kill 进程,launchd KeepAlive 自动拉起 + 健康检查(无需 sudo)
#   start     确保 MySQL + 应用在跑(通常无需手动,已配 RunAtLoad)
#   stop      真正停服 —— 需 sudo,bootout LaunchDaemon(仅 kill 会被 KeepAlive 立刻拉起)
#   logs      tail 应用日志
#   rollback  回滚到 backups/ 里最近一个 jar
set -euo pipefail

HOST="iospub@10.2.54.235"
REMOTE_DIR="/Users/iospub/intranet_app_manager"
START_SCRIPT="start_appmanager.sh"
LABEL="com.truckerpath.appmanager"
HTTP_PORT="8082"
HEALTH_PATH="/apps"
PROC_PATTERN="intranet_app_manager-.*\.jar"

CMD="${1:-status}"

case "$CMD" in
  status)
    ssh "$HOST" PROC_PATTERN="$PROC_PATTERN" HTTP_PORT="$HTTP_PORT" HEALTH_PATH="$HEALTH_PATH" \
        REMOTE_DIR="$REMOTE_DIR" START_SCRIPT="$START_SCRIPT" LABEL="$LABEL" 'bash -s' <<'REMOTE'
      echo "=== 进程 ==="; ps aux | grep -E "$PROC_PATTERN" | grep -v grep || echo "(无)"
      echo "=== 端口 8082/8081 ==="; lsof -nP -iTCP:8082 -sTCP:LISTEN 2>/dev/null | tail -1; lsof -nP -iTCP:8081 -sTCP:LISTEN 2>/dev/null | tail -1
      echo "=== 健康码 ==="; curl -s -o /dev/null -w "  localhost:$HTTP_PORT$HEALTH_PATH -> %{http_code}\n" "http://localhost:$HTTP_PORT$HEALTH_PATH"
      echo "=== 当前 jar(启动脚本引用)==="; grep -oE 'intranet_app_manager-[^ ]*\.jar' "$REMOTE_DIR/$START_SCRIPT" | head -1
      echo "=== launchd ==="; launchctl print "system/$LABEL" 2>/dev/null | grep -E "state =|pid =" || sudo launchctl print "system/$LABEL" 2>/dev/null | grep -E "state =|pid =" || echo "  (需 sudo 查看 system 域)"
REMOTE
    ;;

  restart)
    ssh "$HOST" PROC_PATTERN="$PROC_PATTERN" HTTP_PORT="$HTTP_PORT" HEALTH_PATH="$HEALTH_PATH" REMOTE_DIR="$REMOTE_DIR" 'bash -s' <<'REMOTE'
      PID=$(pgrep -f "$PROC_PATTERN" || true)
      [ -n "$PID" ] && { echo "kill PID=$PID(launchd 自动重启)"; kill "$PID"; } || echo "无运行进程,等待 launchd 拉起"
      echo -n "健康检查 "; CODE=000
      for i in $(seq 1 45); do sleep 2; CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$HTTP_PORT$HEALTH_PATH" || echo 000); [ "$CODE" = "200" ] && break; printf '.'; done
      echo " -> $CODE"; [ "$CODE" = "200" ] && echo "✅ 已重启" || { echo "❌ 失败,看 $REMOTE_DIR/launchd.log"; exit 1; }
REMOTE
    ;;

  start)
    ssh "$HOST" 'mysql.server status >/dev/null 2>&1 || mysql.server start; sudo launchctl kickstart -k system/com.truckerpath.appmanager 2>/dev/null || echo "如未起来,请手动 sudo launchctl bootstrap system /Library/LaunchDaemons/com.truckerpath.appmanager.plist"'
    ;;

  stop)
    echo "⚠️ 真正停服需 sudo(仅 kill 会被 KeepAlive 立即拉起)。在 235 上执行:"
    echo "  sudo launchctl bootout system/$LABEL"
    echo "恢复: sudo launchctl bootstrap system /Library/LaunchDaemons/$LABEL.plist"
    ;;

  logs)
    ssh "$HOST" "tail -${2:-100} $REMOTE_DIR/launchd.log"
    ;;

  rollback)
    ssh "$HOST" REMOTE_DIR="$REMOTE_DIR" START_SCRIPT="$START_SCRIPT" PROC_PATTERN="$PROC_PATTERN" HTTP_PORT="$HTTP_PORT" HEALTH_PATH="$HEALTH_PATH" 'bash -s' <<'REMOTE'
      set -euo pipefail
      cd "$REMOTE_DIR"
      LAST=$(ls -1t backups/intranet_app_manager-*.jar.* 2>/dev/null | head -1 || true)
      [ -n "$LAST" ] || { echo "backups/ 中无可回滚 jar"; exit 1; }
      # 还原文件名(去掉时间戳后缀)
      BASE=$(basename "$LAST"); ORIG="${BASE%.*}"   # intranet_app_manager-x.y.z.jar
      echo "回滚到: $ORIG (来自 $LAST)"
      cp -p "$LAST" "$ORIG"
      CUR_REF=$(grep -oE 'intranet_app_manager-[^ ]*\.jar' "$START_SCRIPT" | head -1 || true)
      [ "$CUR_REF" != "$ORIG" ] && sed -i '' "s|$CUR_REF|$ORIG|g" "$START_SCRIPT" && echo "启动脚本改回 $ORIG"
      PID=$(pgrep -f "$PROC_PATTERN" || true); [ -n "$PID" ] && kill "$PID"
      echo -n "健康检查 "; CODE=000
      for i in $(seq 1 45); do sleep 2; CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$HTTP_PORT$HEALTH_PATH" || echo 000); [ "$CODE" = "200" ] && break; printf '.'; done
      echo " -> $CODE"; [ "$CODE" = "200" ] && echo "✅ 回滚成功" || { echo "❌ 回滚后仍异常,看 launchd.log"; exit 1; }
REMOTE
    ;;

  *) echo "用法: service.sh <status|restart|stop|start|logs|rollback>"; exit 1 ;;
esac
