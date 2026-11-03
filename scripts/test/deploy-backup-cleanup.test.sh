#!/usr/bin/env bash
# 测试 deploy.sh 的 cleanup_auto_backups（从 deploy.sh 提取函数）。macOS/Linux 均可跑。
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
eval "$(sed -n '/^cleanup_auto_backups() {/,/^}/p' "$ROOT/scripts/deploy.sh")"
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
fail=0
mk() { : > "$D/$1"; touch -t "$2" "$D/$1"; }   # 文件名 mtime(YYYYMMDDhhmm)
# 自动备份：旧的(2020年，已过期)与新的(2099年，不过期)
for i in 01 02 03 04 05 06 07 08; do mk "gudu-auto-202001$i-000000-feat-x.sql.gz" 202001${i}0000; done
mk gudu-auto-20200201-000000-feat-x.sql.gz 202002010000
mk gudu-auto-20200202-000000-main.sql.gz 202002020000
mk gudu-auto-20990101-000000-feat-new.sql.gz 209901010000
mk gudu-auto-20990102-000000-feat-new.sql.gz 209901020000
# 名字最老但 mtime 很新：不满足「早于 N 天」，应保留
mk gudu-auto-20190101-000000-recent.sql.gz 209901030000
# 不应被碰
mk gudu-prod-20200101-000000-feat-x.sql.gz 202001010000
mk gudu-prod-20261027-222809-before-recipe-fix.sql.gz 202001010000
mk gudu-prod-before-demo-20261027-015400.sql.gz 202001010000
mk manual-before-x.sql.gz 202001010000
mk before-foo.sql.gz 202001010000
mk gudu-auto-2020010-000000-bad.sql.gz 202001010000
mk gudu-auto-20200101-000000-x.sql.gz.tmp 202001010000

out=$(cleanup_auto_backups "$D" 3 14); echo "$out"
exists() { [ -e "$D/$1" ]; }
chk() { if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; fail=1; fi; }
chk "最新3份保留"       'exists gudu-auto-20990102-000000-feat-new.sql.gz && exists gudu-auto-20990101-000000-feat-new.sql.gz && exists gudu-auto-20200202-000000-main.sql.gz'
chk "超出且过期被删"    '! exists gudu-auto-20200201-000000-feat-x.sql.gz && ! exists gudu-auto-20200108-000000-feat-x.sql.gz && ! exists gudu-auto-20200101-000000-feat-x.sql.gz'
chk "超出但mtime新保留" 'exists gudu-auto-20190101-000000-recent.sql.gz'
for f in gudu-prod-20200101-000000-feat-x.sql.gz gudu-prod-20261027-222809-before-recipe-fix.sql.gz gudu-prod-before-demo-20261027-015400.sql.gz manual-before-x.sql.gz before-foo.sql.gz gudu-auto-2020010-000000-bad.sql.gz gudu-auto-20200101-000000-x.sql.gz.tmp; do
  chk "不碰 $f" "exists $f"
done
chk "打印被删文件名"    'echo "$out" | grep -q "清理旧自动备份: gudu-auto-20200201-000000-feat-x.sql.gz"'
out2=$(cleanup_auto_backups "$D" 100 14); chk "无可删时打印说明" 'echo "$out2" | grep -q "无需清理"'
out3=$(cleanup_auto_backups "$D" abc 14); chk "非法参数跳过" 'echo "$out3" | grep -q "必须是非负整数"'
if [ $fail = 0 ]; then echo "ALL PASS"; else echo "有失败"; exit 1; fi
