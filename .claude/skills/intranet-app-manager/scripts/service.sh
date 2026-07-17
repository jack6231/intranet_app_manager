#!/bin/bash
# intranet_app_manager 服务运维(测服 236 / 正服 235)。
#
# 用法: service.sh [test|prod] <status|restart|stop|start|logs|rollback>
#   环境省略时默认 test。
#
#   status    进程 / 端口 / 健康码 / 当前 jar / (prod) launchd 状态
#   restart   prod: kill 靠 launchd 拉起 / test: kill 后 nohup 重起 + 健康检查
#   start     prod: sudo launchctl kickstart / test: nohup 起
#   stop      prod: 需 sudo bootout(仅 kill 会被 KeepAlive 拉起)/ test: kill 即停
#   logs      tail 应用日志
#   rollback  回滚到 backups/ 最近一个 jar,并按环境方式重启
set -euo pipefail

REMOTE_DIR="/Users/iospub/intranet_app_manager"
START_SCRIPT="start_appmanager.sh"
LABEL="com.truckerpath.appmanager"
HTTP_PORT="8082"
HEALTH_PATH="/apps"
PROC_PATTERN="intranet_app_manager-.*\.jar"

if [ "${1:-}" = "test" ] || [ "${1:-}" = "prod" ]; then ENV="$1"; CMD="${2:-status}"; else ENV="test"; CMD="${1:-status}"; fi
case "$ENV" in
  test) HOST="iospub@10.2.54.236"; RESTART_MODE="nohup" ;;
  prod) HOST="iospub@10.2.54.235"; RESTART_MODE="launchd" ;;
esac

# 远端重启片段(restart/rollback 复用):kill + 按环境拉起 + 健康检查
RESTART_SNIPPET='
  PID=$(pgrep -f "$PROC_PATTERN" || true)
  [ -n "$PID" ] && { echo "kill PID=$PID"; kill "$PID"; } || echo "无运行进程"
  if [ "$RESTART_MODE" = "nohup" ]; then
    for i in 1 2 3 4 5; do pgrep -f "$PROC_PATTERN" >/dev/null || break; sleep 1; done
    pkill -9 -f "$PROC_PATTERN" 2>/dev/null || true
    cd "$REMOTE_DIR" && nohup ./"$START_SCRIPT" > launchd.log 2>&1 </dev/null &
    echo "nohup 重启"
  else
    echo "launchd KeepAlive 自动拉起"
  fi
  echo -n "健康检查 "; C=000
  for i in $(seq 1 45); do sleep 2; C=$(curl -s -o /dev/null -w %{http_code} "http://localhost:$HTTP_PORT$HEALTH_PATH" || echo 000); [ "$C" = 200 ] && break; printf .; done
  echo " -> $C"; [ "$C" = 200 ] && echo "OK" || { echo "失败,看 $REMOTE_DIR/launchd.log"; exit 1; }
'

RENV="PROC_PATTERN=$PROC_PATTERN HTTP_PORT=$HTTP_PORT HEALTH_PATH=$HEALTH_PATH REMOTE_DIR=$REMOTE_DIR START_SCRIPT=$START_SCRIPT RESTART_MODE=$RESTART_MODE LABEL=$LABEL"

echo "[$ENV @ $HOST] $CMD"
case "$CMD" in
  status)
    ssh "$HOST" "$RENV bash -s" <<'REMOTE'
      echo "=== 进程 ==="; ps aux | grep -E "$PROC_PATTERN" | grep -v grep || echo "(无)"
      echo "=== 端口 8082/8081 ==="; lsof -nP -iTCP:8082 -sTCP:LISTEN 2>/dev/null | tail -1; lsof -nP -iTCP:8081 -sTCP:LISTEN 2>/dev/null | tail -1
      echo "=== 健康码 ==="; curl -s -o /dev/null -w "  localhost:$HTTP_PORT$HEALTH_PATH -> %{http_code}\n" "http://localhost:$HTTP_PORT$HEALTH_PATH"
      echo "=== 当前 jar(启动脚本引用)==="; grep -oE 'intranet_app_manager-[^ ]*\.jar' "$REMOTE_DIR/$START_SCRIPT" | head -1
      echo "=== launchd(仅正服 system 域)==="; launchctl print "system/$LABEL" 2>/dev/null | grep -E "state =|pid =" || echo "  (test 无 launchd 或需 sudo)"
REMOTE
    ;;
  restart)
    ssh "$HOST" "$RENV bash -s" <<REMOTE
$RESTART_SNIPPET
REMOTE
    ;;
  start)
    if [ "$ENV" = "prod" ]; then
      echo "正服请: sudo launchctl kickstart -k system/$LABEL"
    else
      ssh "$HOST" "cd $REMOTE_DIR && nohup ./$START_SCRIPT > launchd.log 2>&1 </dev/null & echo started"
    fi
    ;;
  stop)
    if [ "$ENV" = "prod" ]; then
      echo "⚠️ 正服停服需 sudo(仅 kill 会被 KeepAlive 拉起):"
      echo "  sudo launchctl bootout system/$LABEL"
      echo "  恢复: sudo launchctl bootstrap system /Library/LaunchDaemons/$LABEL.plist"
    else
      ssh "$HOST" "pkill -f '$PROC_PATTERN'; sleep 2; pkill -9 -f '$PROC_PATTERN' 2>/dev/null; pgrep -f '$PROC_PATTERN' >/dev/null && echo '仍在运行' || echo '已停(test 无 launchd,不会自动拉起)'"
    fi
    ;;
  logs)
    ssh "$HOST" "tail -${3:-100} $REMOTE_DIR/launchd.log"
    ;;
  rollback)
    ssh "$HOST" "$RENV bash -s" <<REMOTE
      set -euo pipefail
      cd "$REMOTE_DIR"
      LAST=\$(ls -1t backups/intranet_app_manager-*.jar.* 2>/dev/null | head -1 || true)
      [ -n "\$LAST" ] || { echo "backups/ 无可回滚 jar"; exit 1; }
      BASE=\$(basename "\$LAST"); ORIG="\${BASE%.*}"
      echo "回滚到: \$ORIG"
      cp -p "\$LAST" "\$ORIG"
      CUR=\$(grep -oE 'intranet_app_manager-[^ ]*\.jar' "$START_SCRIPT" | head -1 || true)
      [ "\$CUR" != "\$ORIG" ] && sed -i '' "s|\$CUR|\$ORIG|g" "$START_SCRIPT" && echo "启动脚本改回 \$ORIG"
$RESTART_SNIPPET
REMOTE
    ;;
  *) echo "用法: service.sh [test|prod] <status|restart|stop|start|logs|rollback>"; exit 1 ;;
esac
