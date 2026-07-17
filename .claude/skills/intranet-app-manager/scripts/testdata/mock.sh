#!/bin/bash
# 在【测服 236】上造多应用测试数据,覆盖清理功能的各分支。
# 用法(从开发机):
#   scp <repo>/src/main/resources/static/images/ding_ding.png iospub@10.2.54.236:~/mock_icon.png
#   scp 本脚本 iospub@10.2.54.236:~/mock.sh
#   ssh iospub@10.2.54.236 'bash ~/mock.sh'
# 生成 3 个 App(TPP Dev/iOS、TPP Android、Legacy App/iOS),各含不同新旧+定版+当前包组合。
# 清理按钮在每个 App 详情页,按 App 独立清理。切勿在正服 235 上跑。
set -e
MYSQL="/opt/homebrew/bin/mysql -u root app_manager"
ROOT="$HOME/intranet_app_manager/static/upload"
ICON="$HOME/mock_icon.png"
NOW=$(( $(date +%s) * 1000 ))
DAY=86400000

BUNDLES=("com.trucker.dev" "com.trucker.android" "com.legacy.app" "com.test.cleanup")
for b in "${BUNDLES[@]}"; do
  $MYSQL -e "UPDATE tb_app SET currentid=NULL WHERE bundleid='$b';"
  $MYSQL -e "DELETE FROM tb_package WHERE bundleid='$b';"
  $MYSQL -e "DELETE FROM tb_app WHERE bundleid='$b';"
done
rm -rf "$ROOT/ios/com.trucker.dev" "$ROOT/ios/com.legacy.app" "$ROOT/android/com.trucker.android" "$ROOT/ios/com.test.cleanup"

uuid() { uuidgen | tr -d '-' | tr 'A-Z' 'a-z'; }
insert_pkg() {  # <pid> <appid> <bundle> <platform> <createTime> <isRelease> <name> <version> <build>
  local pid=$1 appid=$2 bundle=$3 platform=$4 t=$5 rel=$6 name=$7 ver=$8 build=$9
  local ext=ipa; [ "$platform" = android ] && ext=apk
  $MYSQL -e "INSERT INTO tb_package (id,build_version,bundleid,create_time,file_name,is_release,min_version,name,platform,size,version,app_id) \
    VALUES ('$pid','$build','$bundle',$t,'$platform.$ext',$rel,'14.0','$name','$platform',$((RANDOM*100000)),'$ver','$appid');"
  local dir="$ROOT/$platform/$bundle/$t"; mkdir -p "$dir"; cp "$ICON" "$dir/icon.png"; echo "dummy $ver" > "$dir/$platform.$ext"
}

A1=$(uuid); $MYSQL -e "INSERT INTO tb_app (id,bundleid,create_time,name,platform,short_code) VALUES ('$A1','com.trucker.dev',$((NOW-DAY)),'TPP Dev','ios','DEV1');"
p=(); for i in 1 2 3 4 5 6; do p+=($(uuid)); done
insert_pkg "${p[0]}" "$A1" com.trucker.dev ios $((NOW-45*DAY)) 0 "TPP Dev" 7.0.0 70000
insert_pkg "${p[1]}" "$A1" com.trucker.dev ios $((NOW-30*DAY)) 0 "TPP Dev" 7.1.0 71000
insert_pkg "${p[2]}" "$A1" com.trucker.dev ios $((NOW-20*DAY)) 0 "TPP Dev" 7.2.0 72000
insert_pkg "${p[3]}" "$A1" com.trucker.dev ios $((NOW-10*DAY)) 1 "TPP Dev" 7.3.0 73000
insert_pkg "${p[4]}" "$A1" com.trucker.dev ios $((NOW-3*DAY))  0 "TPP Dev" 7.4.0 74000
insert_pkg "${p[5]}" "$A1" com.trucker.dev ios $((NOW-1*DAY))  0 "TPP Dev" 7.5.0 75000
$MYSQL -e "UPDATE tb_app SET currentid='${p[5]}' WHERE id='$A1';"

A2=$(uuid); $MYSQL -e "INSERT INTO tb_app (id,bundleid,create_time,name,platform,short_code) VALUES ('$A2','com.trucker.android',$((NOW-2*DAY)),'TPP Android','android','AND1');"
q=(); for i in 1 2 3 4 5; do q+=($(uuid)); done
insert_pkg "${q[0]}" "$A2" com.trucker.android android $((NOW-60*DAY)) 0 "TPP Android" 5.0.0 5000
insert_pkg "${q[1]}" "$A2" com.trucker.android android $((NOW-40*DAY)) 0 "TPP Android" 5.1.0 5100
insert_pkg "${q[2]}" "$A2" com.trucker.android android $((NOW-15*DAY)) 1 "TPP Android" 5.2.0 5200
insert_pkg "${q[3]}" "$A2" com.trucker.android android $((NOW-8*DAY))  0 "TPP Android" 5.3.0 5300
insert_pkg "${q[4]}" "$A2" com.trucker.android android $((NOW-2*DAY))  0 "TPP Android" 5.4.0 5400
$MYSQL -e "UPDATE tb_app SET currentid='${q[4]}' WHERE id='$A2';"

A3=$(uuid); $MYSQL -e "INSERT INTO tb_app (id,bundleid,create_time,name,platform,short_code) VALUES ('$A3','com.legacy.app',$((NOW-90*DAY)),'Legacy App','ios','LEG1');"
r=(); for i in 1 2 3 4; do r+=($(uuid)); done
insert_pkg "${r[0]}" "$A3" com.legacy.app ios $((NOW-90*DAY)) 0 "Legacy App" 1.0.0 100
insert_pkg "${r[1]}" "$A3" com.legacy.app ios $((NOW-80*DAY)) 0 "Legacy App" 1.1.0 110
insert_pkg "${r[2]}" "$A3" com.legacy.app ios $((NOW-70*DAY)) 1 "Legacy App" 1.2.0 120
insert_pkg "${r[3]}" "$A3" com.legacy.app ios $((NOW-60*DAY)) 0 "Legacy App" 1.3.0 130
$MYSQL -e "UPDATE tb_app SET currentid='${r[0]}' WHERE id='$A3';"

echo "===== 生成完毕 ====="
$MYSQL -e "SELECT a.name, a.platform, a.id AS app_id, COUNT(p.id) AS pkgs FROM tb_app a LEFT JOIN tb_package p ON p.app_id=a.id WHERE a.bundleid IN ('com.trucker.dev','com.trucker.android','com.legacy.app') GROUP BY a.id;"
