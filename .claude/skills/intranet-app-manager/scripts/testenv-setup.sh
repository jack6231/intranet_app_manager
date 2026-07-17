#!/bin/bash
# 从零在【测服 236】搭建 intranet_app_manager 并部署运行(nohup)。
# 236 是共享测试机,策略为"用时现搭、用完清理干净"——测完请跑 testenv-teardown.sh。
# 从开发机执行:bash testenv-setup.sh
# 前置:236 已有 JDK17、brew;本机能免密 ssh iospub@10.2.54.236。
set -euo pipefail

HOST="iospub@10.2.54.236"
DOMAIN="10.2.54.236"
DIR="/Users/iospub/intranet_app_manager"
BREW="/opt/homebrew/bin"
JAVA17="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/java"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IAM_REPO="${IAM_REPO:-$(cd "$SCRIPT_DIR/../../../.." && pwd)}"
log() { printf '\033[1;36m[testenv-setup]\033[0m %s\n' "$*"; }

# 1) 构建 jar
log "构建 jar(JDK11)"
( cd "$IAM_REPO" && ./gradlew clean bootJar )
JAR="$(ls -t "$IAM_REPO"/build/libs/intranet_app_manager-*.jar | head -1)"
JAR_NAME="$(basename "$JAR")"
log "jar = $JAR_NAME"

# 2) 确保 MySQL 装好、起来、库存在(mysql.server 前台会占 ssh,故 detached 起 + 轮询端口)
log "确保 MySQL(装/起/建库)"
ssh "$HOST" "bash -lc '
  $BREW/brew list mysql >/dev/null 2>&1 || $BREW/brew install mysql >/dev/null
  if ! /usr/sbin/lsof -nP -iTCP:3306 -sTCP:LISTEN >/dev/null 2>&1; then
    nohup $BREW/mysql.server start >/dev/null 2>&1 </dev/null &
  fi
'"
# 轮询 3306
for i in $(seq 1 30); do
  if ssh "$HOST" '/usr/bin/nc -z 127.0.0.1 3306' 2>/dev/null; then break; fi
  sleep 2
done
ssh "$HOST" "$BREW/mysql -u root -e \"CREATE DATABASE IF NOT EXISTS app_manager DEFAULT CHARSET utf8 COLLATE utf8_general_ci;\""

# 3) 目录 + 外置资源(config / 证书 / static)
log "建目录 + 写 config + 传证书"
ssh "$HOST" "mkdir -p $DIR/config $DIR/static/upload $DIR/static/crt"
scp -q "$IAM_REPO/src/main/resources/server.pkcs12" "$HOST:$DIR/server.pkcs12"
ssh "$HOST" "cat > $DIR/config/application.properties" <<PROPS
spring.datasource.url=jdbc:mysql://localhost:3306/app_manager?useUnicode=true&characterEncoding=utf-8&allowPublicKeyRetrieval=true&useSSL=false
spring.datasource.username=root
spring.datasource.password=
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.database=MYSQL
spring.jpa.show-sql=false
spring.jpa.hibernate.ddl-auto=update
spring.jpa.hibernate.naming-strategy=org.hibernate.cfg.ImprovedNamingStrategy
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL5InnoDBDialect
spring.servlet.multipart.max-file-size=800MB
spring.servlet.multipart.max-request-size=800MB
server.ssl.key-store=server.pkcs12
server.ssl.key-store-password=123456
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=1
server.port=8081
server.http.port=8082
config.debug=release
server.domain=$DOMAIN
PROPS

# 4) 启动脚本
ssh "$HOST" "cat > $DIR/start_appmanager.sh" <<START
#!/bin/bash
for i in \$(seq 1 60); do /usr/bin/nc -z 127.0.0.1 3306 2>/dev/null && break; sleep 2; done
exec $JAVA17 -jar $DIR/$JAR_NAME
START
ssh "$HOST" "chmod +x $DIR/start_appmanager.sh"

# 5) 传 jar + nohup 启动 + 健康检查
log "传 jar 并启动"
scp -q "$JAR" "$HOST:$DIR/$JAR_NAME"
ssh "$HOST" "cd $DIR && pkill -f 'intranet_app_manager-.*\.jar' 2>/dev/null; sleep 1; nohup ./start_appmanager.sh > launchd.log 2>&1 </dev/null & echo started"
echo -n "  健康检查 "
for i in $(seq 1 40); do
  sleep 2
  C=$(ssh "$HOST" "curl -s -o /dev/null -w %{http_code} http://localhost:8082/apps" 2>/dev/null || echo 000)
  [ "$C" = 200 ] && { echo " -> 200"; break; }
  printf '.'
  [ "$i" = 40 ] && echo " -> 超时($C)"
done

log "完成。测服: http://$DOMAIN:8082/apps"
log "造数据: 见 scripts/testdata/mock.sh ;用完清理: bash scripts/testenv-teardown.sh"
