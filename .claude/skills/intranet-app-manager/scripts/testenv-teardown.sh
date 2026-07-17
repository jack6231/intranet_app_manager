#!/bin/bash
# 彻底清理【测服 236】:停 app → 删库(全部数据)→ 删部署目录 → 停 MySQL → 清临时脚本。
# 从开发机执行:bash testenv-teardown.sh
# 说明:保留 MySQL 的 brew 安装(引擎本身非"我们的数据",下次 setup 直接复用,免重装)。
#      如需连 MySQL 一起彻底卸载,见末尾注释。
set -euo pipefail

HOST="iospub@10.2.54.236"
DIR="/Users/iospub/intranet_app_manager"
BREW="/opt/homebrew/bin"
log() { printf '\033[1;36m[testenv-teardown]\033[0m %s\n' "$*"; }

log "停 app + 删库 + 删目录 + 停 MySQL"
ssh "$HOST" "bash -lc '
  pkill -f \"intranet_app_manager-.*\\.jar\" 2>/dev/null || true
  sleep 2
  pkill -9 -f \"intranet_app_manager-.*\\.jar\" 2>/dev/null || true
  $BREW/mysql -u root -e \"DROP DATABASE IF EXISTS app_manager;\" 2>/dev/null || true
  rm -rf $DIR
  rm -f ~/mock.sh ~/mock_icon.png ~/seed_236.sh ~/test_clean_236.sh
  $BREW/mysql.server stop >/dev/null 2>&1 || true
  echo done
'"

log "校验:app 进程 / 端口 / 目录 应全部清空"
ssh "$HOST" "bash -lc '
  pgrep -f \"intranet_app_manager-.*\\.jar\" >/dev/null && echo \"⚠️ app 仍在\" || echo \"  app: 已停\"
  /usr/sbin/lsof -nP -iTCP:8082 -sTCP:LISTEN >/dev/null 2>&1 && echo \"⚠️ 8082 占用\" || echo \"  8082: 已释放\"
  [ -d $DIR ] && echo \"⚠️ 目录仍在\" || echo \"  目录: 已删\"
'"
log "测服已清理干净。下次: bash scripts/testenv-setup.sh"

# 如需连 MySQL 也彻底卸载(通常不必):
#   ssh iospub@10.2.54.236 '/opt/homebrew/bin/brew uninstall mysql && rm -rf /opt/homebrew/var/mysql'
