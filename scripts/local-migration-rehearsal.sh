#!/usr/bin/env bash
# =====================================================================================
# multi-tenant-family 迁移演练（V75–V79）：迁移 → 校验 → V77 关键断言 → 回滚 → 结构对比
#
# 依赖：bash + mysql 客户端（以及 coreutils：sort / diff / awk / sha256sum 或 shasum）。不依赖 python。
#
# 用法：
#   MYSQL_CMD="docker exec -i gudu-rehearsal-mysql mysql -uroot -pXXX" \
#     scripts/local-migration-rehearsal.sh --from-scratch [DB_NAME]
#   MYSQL_CMD="docker exec -i gudu-rehearsal-mysql mysql -uroot -pXXX" \
#     scripts/local-migration-rehearsal.sh --existing [DB_NAME]
#
#   DB_NAME 默认 gudu_rehearsal；也可用环境变量 DB_NAME。
#   安全闸：拒绝库名 gudu，库名必须以 _rehearsal 结尾（防止误跑到真库）。
#   MYSQL_CMD 按空格切词执行，密码里不要含空格；脚本只往它的 stdin 喂 SQL，不读写其他地址。
#
# 模式：
#   --from-scratch  DROP + CREATE 目标库 → 按 deploy.sh 的顺序（sort -V）执行全部 V01–V74
#                   → 灌入仿生产夹具（纯微信自动注册账号、超管、带健康档案的成员、宝宝档案、演示作者、
#                   各家庭域表数据、公共菜谱）→ 演练。
#   --existing      目标库已是预发 gudu 的复制品：按 schema_migration_log（deploy.sh 维护的登记表，
#                   文件名 + sha256）补执行尚未执行的 V<75 迁移（V<42 只登记，与 deploy.sh 一致）→ 演练。
#   两种模式在回滚验证结束后，会重新执行 V75–V79，再按序执行其后（V80+）尚未登记的迁移，
#   确认后续脚本能在多家庭结构上跑通；最终库处于「全部迁移已执行」状态。
#
# 演练（两种模式相同，同一个库上完成）：
#   1. 导出迁移前结构快照（information_schema：表 / 列 / 索引）
#   2. 执行 V75–V79 并登记
#   3. 跑 menu-api/sql/verify/tenant_migration_check.sql：第一段每行 bad 必须为 0
#   4. V77 关键断言（纯微信账号各自一个家且不在默认家庭、超管 = SUPER_ADMIN + 默认家庭 OWNER、
#      家庭域表 family_id 全部 NOT NULL 且无空值、旧密码哈希原样迁入……）
#   5. 执行 sql/rollback/V75-V79_multi_tenant_family.sql
#   6. 导出回滚后结构快照，与迁移前逐行对比，必须一致
# 任一断言失败 → 打印原因，退出码 1。
# =====================================================================================
set -euo pipefail

MODE="${1:-}"
DB_NAME="${2:-${DB_NAME:-gudu_rehearsal}}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQL_DIR="${ROOT}/menu-api/sql"
VERIFY_SQL="${SQL_DIR}/verify/tenant_migration_check.sql"
ROLLBACK_SQL="${SQL_DIR}/rollback/V75-V79_multi_tenant_family.sql"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/gudu-rehearsal.XXXXXX")"
TARGET_FILES=(V75__account_family.sql V76__tenant_family_columns.sql V77__tenant_data_migration.sql
              V78__tenant_family_not_null.sql V79__account_merge_log.sql)
TENANT_TABLES=(member menu menu_dish menu_prep_status menu_activity menu_invite menu_join menu_cook_usage
               menu_template ingredient_stock pantry pantry_change_log stock_log shopping_list shopping_item
               cooking_record favorite daily_log daily_log_item review review_score dish_draft dish_history
               meal_plan meal_plan_item meal_plan_draft meal_plan_slot notification)
FAILURES=0

usage() {
  sed -n '2,40p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 2
}

die() { echo "❌ $*" >&2; exit 1; }
info() { echo "→ $*"; }
fail() { echo "  ✗ $*"; FAILURES=$((FAILURES + 1)); }
pass() { echo "  ✓ $*"; }

# ------------------------------------------------------------------ 参数与安全闸
case "${MODE}" in
  --from-scratch|--existing) ;;
  -h|--help|"") usage ;;
  *) echo "未知模式: ${MODE}"; usage ;;
esac
[ -n "${MYSQL_CMD:-}" ] || die "请设置 MYSQL_CMD，例如 MYSQL_CMD=\"docker exec -i gudu-rehearsal-mysql mysql -uroot -pXXX\""
[[ "${DB_NAME}" =~ ^[A-Za-z0-9_]+$ ]] || die "库名只允许字母数字下划线: ${DB_NAME}"
[ "${DB_NAME}" != "gudu" ] || die "拒绝在库 gudu 上演练（真库）"
[[ "${DB_NAME}" == *_rehearsal ]] || die "库名必须以 _rehearsal 结尾（当前: ${DB_NAME}），防止误跑到真库"
for f in "${TARGET_FILES[@]}"; do [ -f "${SQL_DIR}/${f}" ] || die "缺少迁移文件 ${f}"; done
for f in $(cd "${SQL_DIR}" && printf '%s\n' V*.sql); do
  [[ "${f}" =~ ^V[0-9]+__[A-Za-z0-9_]+\.sql$ ]] || die "非法迁移文件名: ${f}"
done
[ -f "${VERIFY_SQL}" ] || die "缺少 ${VERIFY_SQL}"
[ -f "${ROLLBACK_SQL}" ] || die "缺少 ${ROLLBACK_SQL}"

# MYSQL_CMD 按空格切词（有意不加引号）
# shellcheck disable=SC2086
mysql_server() { ${MYSQL_CMD} -N -B; }
# shellcheck disable=SC2086
mysql_db() { ${MYSQL_CMD} -N -B "${DB_NAME}"; }
q() { echo "$1" | mysql_db; }

file_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

register() { # 登记迁移（与 deploy.sh 同表同格式）
  printf "INSERT IGNORE INTO schema_migration_log(filename, checksum) VALUES ('%s','%s');\n" "$1" "$(file_sha256 "${SQL_DIR}/$1")" \
    | mysql_db
}

apply_file() {
  info "apply $1"
  mysql_db < "${SQL_DIR}/$1" > "${WORK}/apply_$1.log" || die "执行 $1 失败（输出见 ${WORK}/apply_$1.log）"
  register "$1"
}

version_of() { echo "$1" | sed -E 's/^V([0-9]+)__.*/\1/'; }

all_migrations() {
  local name
  for name in $(cd "${SQL_DIR}" && printf '%s\n' V*.sql | sort -V); do
    [[ "${name}" =~ ^V[0-9]+__[A-Za-z0-9_]+\.sql$ ]] || die "非法迁移文件名: ${name}"
    echo "${name}"
  done
}

snapshot() { # 结构快照：表 / 列 / 索引（不含 AUTO_INCREMENT 等数据性属性）
  q "SELECT 'T', TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME;
     SELECT 'C', TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE,
            IFNULL(COLUMN_DEFAULT, '<null>'), EXTRA, COLUMN_COMMENT
       FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, ORDINAL_POSITION;
     SELECT 'I', TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME, NON_UNIQUE
       FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;" \
    > "$1"
}

expect_zero() { # expect_zero "说明" "SELECT COUNT(*) ..."
  local n
  n="$(q "$2" | tail -n 1)"
  if [ "${n}" = "0" ]; then pass "$1"; else fail "$1（实际 ${n}）"; fi
}

expect_positive() {
  local n
  n="$(q "$2" | tail -n 1)"
  if [ -n "${n}" ] && [ "${n}" != "0" ] && [ "${n}" != "NULL" ]; then pass "$1（${n}）"; else fail "$1（实际 ${n:-空}）"; fi
}

table_exists() {
  [ "$(q "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '$1'")" = "1" ]
}

# ------------------------------------------------------------------ 准备库
prepare_from_scratch() {
  info "重建库 ${DB_NAME}（--from-scratch）"
  echo "DROP DATABASE IF EXISTS \`${DB_NAME}\`; CREATE DATABASE \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" \
    | mysql_server
  mysql_db < "${SQL_DIR}/V63__schema_migration_log.sql"
  local name v
  for name in $(all_migrations); do
    v=$((10#$(version_of "${name}")))
    [ "${v}" -lt 75 ] || continue
    apply_file "${name}"
  done
  load_fixture
}

prepare_existing() {
  info "使用已有库 ${DB_NAME}（--existing，预发数据复制品）"
  [ "$(echo "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '${DB_NAME}'" | mysql_server)" = "1" ] \
    || die "库 ${DB_NAME} 不存在"
  table_exists schema_migration_log || die "库里没有 schema_migration_log（不是 deploy.sh 维护过的库）"
  local cnt
  cnt="$(q "SELECT COUNT(*) FROM schema_migration_log")"
  [ "${cnt}" != "0" ] || die "schema_migration_log 为空，无法判断哪些迁移已执行；请确认复制的是预发库"
  for f in "${TARGET_FILES[@]}"; do
    [ "$(q "SELECT COUNT(*) FROM schema_migration_log WHERE filename = '${f}'")" = "0" ] \
      || die "${f} 已登记为执行过——该库已迁移，请重新复制预发库后再演练"
  done
  local registered name v sum reg
  registered="$(q "SELECT filename, checksum FROM schema_migration_log")"
  for name in $(all_migrations); do
    v=$((10#$(version_of "${name}")))
    [ "${v}" -lt 75 ] || continue
    sum="$(file_sha256 "${SQL_DIR}/${name}")"
    reg="$(echo "${registered}" | awk -F'\t' -v f="${name}" '$1==f {print $2}')"
    if [ -n "${reg}" ]; then
      [ "${reg}" = "${sum}" ] || die "${name} 已执行但内容与登记的 checksum 不一致（deploy.sh 会中止部署）"
      continue
    fi
    if [ "${v}" -lt 42 ]; then
      info "register-only ${name}（V<42 由 initdb 负责，与 deploy.sh 一致）"
      register "${name}"
    else
      apply_file "${name}"
    fi
  done
}

# ------------------------------------------------------------------ 仿生产夹具（仅 --from-scratch）
load_fixture() {
  info "灌入仿生产夹具"
  mysql_db <<'SQL'
-- 超管：V29 已从 user 迁入 phone='admin'；兜底补建，再加第二个超管（验证「最小 id 超管为 OWNER，其余 ADMIN」）
INSERT INTO member(name, phone, password_hash, is_admin, role_tags, health_profile)
SELECT '掌勺人', 'admin', '$2a$10$UM6Ql8.rTKA1aGBSup2ACu4ol4WikGIlfyAPPSciymgdPMpWqa/Ba', 1, '32', '{}'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM member WHERE phone = 'admin');
INSERT INTO member(name, phone, password_hash, is_admin, role_tags) VALUES
  ('演练副超管', 'rh_admin2', '$2a$10$UM6Ql8.rTKA1aGBSup2ACu4ol4WikGIlfyAPPSciymgdPMpWqa/Ba', 1, '32');
-- 超管代建的手机号账号（带健康档案）
INSERT INTO member(name, phone, password_hash, is_admin, role_tags, health_profile) VALUES
  ('演练妈妈', '13800009001', '$2a$10$8fMxXFI3W4XzBun3fNpUIuXT4dN9CRvHcw6K7edMJU78705ETwVrK', 0, '34',
   '{"height": 160, "weight": 55, "age": 35, "gender": "female", "audiences": ["高血压"]}');
-- 宝宝档案（无任何凭证）
INSERT INTO member(name, is_admin, role_tags, health_profile) VALUES
  ('演练宝宝', 0, '34', '{"age": 1, "audiences": ["宝宝辅食"]}');
-- 纯微信自动注册账号 ×3（wx-login 自动建号：占位手机号、无密码、无健康档案）
INSERT INTO member(name, phone, openid, is_admin) VALUES
  ('微信用户', 'wx_rhwx0001', 'rh_wx_0001', 0),
  ('微信用户', 'wx_rhwx0002', 'rh_wx_0002', 0),
  ('微信用户', 'wx_rhwx0003', 'rh_wx_0003', 0);
-- 微信账号但已建健康档案（真实家庭成员 → 应进默认家庭）
INSERT INTO member(name, phone, openid, is_admin, role_tags, health_profile) VALUES
  ('演练爸爸', 'wx_rhwx0004', 'rh_wx_0004', 0, '32', '{"height": 175, "weight": 70, "age": 38, "gender": "male"}');
-- 已删除的微信账号（不应生成 account）
INSERT INTO member(name, phone, openid, is_admin, deleted) VALUES ('已删微信用户', 'wx_rhwx0009', 'rh_wx_0009', 0, 1);
-- 厨房圈演示作者（无凭证、cm_ 前缀 → 占位家庭 2）
INSERT INTO member(name, phone, is_admin) VALUES ('糖糖酱', 'cm_rh_tang', 0);

-- 公共菜谱
INSERT INTO dish(name, prep_time, cook_time, difficulty, source, deleted) VALUES ('演练公共菜', 5, 10, 1, 'ORIGINAL', 0);
SET @dish := LAST_INSERT_ID();
SET @mama := (SELECT id FROM member WHERE phone = '13800009001');
SET @wx1 := (SELECT id FROM member WHERE openid = 'rh_wx_0001');
SET @wx2 := (SELECT id FROM member WHERE openid = 'rh_wx_0002');

-- 家庭域数据（含陌生微信用户产生的数据：迁移后归默认家庭，他们自己的新家看不到）
INSERT INTO menu(name, target_member_id, serving_count) VALUES ('演练食集', @mama, 2);
SET @menu := LAST_INSERT_ID();
INSERT INTO menu(name, target_member_id, serving_count) VALUES ('微信用户的食集', @wx1, 1);
INSERT INTO menu_dish(menu_id, dish_id, serving_factor) VALUES (@menu, @dish, 1);
INSERT INTO ingredient_stock(ingredient_id, level)
  SELECT i.id, 'LOW' FROM ingredient i
  WHERE NOT EXISTS (SELECT 1 FROM ingredient_stock s WHERE s.ingredient_id = i.id) ORDER BY i.id LIMIT 3;
INSERT INTO shopping_list(name) VALUES ('演练采购清单');
INSERT INTO cooking_record(dish_id, member_id, cooked_at) VALUES (@dish, @mama, NOW()), (@dish, @wx1, NOW());
INSERT INTO favorite(member_id, dish_id) VALUES (@mama, @dish), (@wx2, @dish);
INSERT INTO review(dish_id, member_id, star_rating, text) VALUES (@dish, @mama, 5, '演练点评');
INSERT INTO notification(member_id, type, channel, title) VALUES (@mama, 'expiry', 'in_app', '演练通知');
INSERT INTO dish_draft(user_id, name) VALUES (@wx1, '演练草稿');
INSERT INTO daily_log(member_id, `date`) VALUES (@mama, CURDATE());
SQL
  pass "夹具已灌入"
}

# ------------------------------------------------------------------ 断言
verify_check_sql() {
  info "校验 SQL：${VERIFY_SQL}"
  # 第一条语句（到第一个分号）= bad 必须为 0 的检查项；其余为信息项
  awk 'BEGIN{RS=";"} NR==1 {print $0 ";"}' "${VERIFY_SQL}" > "${WORK}/verify_first.sql"
  mysql_db < "${WORK}/verify_first.sql" > "${WORK}/verify_first.out"
  local bad
  bad="$(awk -F'\t' '$2 != "0" {print}' "${WORK}/verify_first.out")"
  if [ -z "${bad}" ]; then
    pass "校验项全部为 0（$(wc -l < "${WORK}/verify_first.out" | tr -d ' ') 项）"
  else
    fail "校验项非 0："
    echo "${bad}" | sed 's/^/      /'
  fi
  echo "  —— 信息项 / E.8 疑似重复账号（人工核对）——"
  mysql_db < "${VERIFY_SQL}" | tail -n +"$(( $(wc -l < "${WORK}/verify_first.out") + 1 ))" | sed 's/^/      /' || true
}

v77_assertions() {
  info "V77 关键断言"
  local wx_only="m.openid IS NOT NULL AND m.password_hash IS NULL AND m.health_profile IS NULL
                 AND COALESCE(m.is_admin, 0) = 0 AND m.deleted = 0"
  expect_positive "存在纯微信自动注册账号（样本非空）" \
    "SELECT COUNT(*) FROM member m JOIN account a ON a.id = m.id WHERE ${wx_only}"
  expect_zero "纯微信账号不在默认家庭（看不到默认家庭数据）" \
    "SELECT COUNT(*) FROM member m JOIN family_membership fm ON fm.account_id = m.id WHERE ${wx_only} AND fm.family_id = 1"
  expect_zero "纯微信账号各自恰好一个家庭且为 OWNER" \
    "SELECT COUNT(*) FROM member m JOIN account a ON a.id = m.id WHERE ${wx_only} AND (
       (SELECT COUNT(*) FROM family_membership fm WHERE fm.account_id = a.id) <> 1
       OR NOT EXISTS (SELECT 1 FROM family_membership fm JOIN family f ON f.id = fm.family_id
                      WHERE fm.account_id = a.id AND fm.role = 'OWNER' AND f.id > 2 AND f.status = 'ACTIVE'))"
  expect_zero "纯微信账号的家庭互不相同" \
    "SELECT COUNT(*) FROM (SELECT fm.family_id FROM member m JOIN family_membership fm ON fm.account_id = m.id
       WHERE ${wx_only} GROUP BY fm.family_id HAVING COUNT(*) > 1) x"
  local tbl sum_sql=""
  for tbl in "${TENANT_TABLES[@]}"; do
    [ "${tbl}" = "member" ] && continue
    table_exists "${tbl}" || continue
    sum_sql="${sum_sql} + (SELECT COUNT(*) FROM ${tbl} t WHERE t.family_id IN (SELECT fm.family_id FROM family_membership fm
      JOIN member m ON m.id = fm.account_id WHERE ${wx_only}))"
  done
  expect_zero "纯微信账号的新家里没有任何存量业务数据" "SELECT 0 ${sum_sql}"
  expect_zero "纯微信账号的档案已迁到自己的家（不在默认家庭）" \
    "SELECT COUNT(*) FROM member m WHERE ${wx_only} AND m.family_id = 1"
  expect_zero "最小 id 的超管 = SUPER_ADMIN 且为默认家庭 OWNER" \
    "SELECT COUNT(*) FROM (SELECT MIN(a.id) AS id FROM account a JOIN member m ON m.id = a.id
       WHERE m.is_admin = 1 AND m.deleted = 0) s
     WHERE s.id IS NULL OR NOT EXISTS (SELECT 1 FROM account a WHERE a.id = s.id AND a.platform_role = 'SUPER_ADMIN')
        OR NOT EXISTS (SELECT 1 FROM family_membership fm WHERE fm.family_id = 1 AND fm.account_id = s.id AND fm.role = 'OWNER')
        OR NOT EXISTS (SELECT 1 FROM family f WHERE f.id = 1 AND f.owner_account_id = s.id)"
  expect_zero "全部超管为 SUPER_ADMIN、在默认家庭且角色为 OWNER/ADMIN" \
    "SELECT COUNT(*) FROM member m LEFT JOIN account a ON a.id = m.id
       LEFT JOIN family_membership fm ON fm.account_id = m.id AND fm.family_id = 1
     WHERE m.is_admin = 1 AND m.deleted = 0
       AND (a.id IS NULL OR a.platform_role <> 'SUPER_ADMIN' OR fm.role IS NULL OR fm.role NOT IN ('OWNER', 'ADMIN'))"
  expect_zero "旧密码哈希原样迁入 account（密码不变）" \
    "SELECT COUNT(*) FROM member m JOIN account a ON a.id = m.id
     WHERE m.password_hash IS NOT NULL AND NOT (a.password_hash <=> m.password_hash)"
  expect_zero "wx_ 占位手机号未迁入 account.phone" "SELECT COUNT(*) FROM account WHERE phone LIKE 'wx\\_%'"
  expect_zero "已删除成员未生成 account" \
    "SELECT COUNT(*) FROM member m JOIN account a ON a.id = m.id WHERE m.deleted = 1 AND m.openid LIKE 'rh\\_%'"
  expect_zero "无凭证的档案（宝宝等）没有 account_id" \
    "SELECT COUNT(*) FROM member WHERE password_hash IS NULL AND openid IS NULL AND account_id IS NOT NULL"
  expect_zero "厨房圈演示作者在占位家庭 2" "SELECT COUNT(*) FROM member WHERE phone LIKE 'cm\\_%' AND family_id <> 2"
  expect_zero "存量菜谱全部进入公共库（family_id IS NULL）" "SELECT COUNT(*) FROM dish WHERE family_id IS NOT NULL"
  for tbl in "${TENANT_TABLES[@]}"; do
    table_exists "${tbl}" || continue
    expect_zero "${tbl}.family_id NOT NULL 且无空值" \
      "SELECT (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = '${tbl}' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'NO') <> 1
            OR (SELECT COUNT(*) FROM ${tbl} WHERE family_id IS NULL) > 0"
  done
  expect_zero "ingredient_stock 唯一键已改为 (family_id, ingredient_id)" \
    "SELECT 1 - COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'ingredient_stock' AND INDEX_NAME = 'uk_stock_family_ingredient'"
}

# V91：账号 id 与档案 id 取值范围错开 + 通知收件人拆列
v91_assertions() {
  info "V91 断言（账号 / 档案 id 范围、通知收件人）"
  table_exists notification || return 0
  [ "$(q "SELECT COUNT(*) FROM schema_migration_log WHERE filename LIKE 'V91\_%'")" != "0" ] || return 0
  expect_zero "account 自增起点 >= 1000 万（新账号 id 与档案 id 取值范围永不重叠）" \
    "SET SESSION information_schema_stats_expiry = 0;
     SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account'
       AND AUTO_INCREMENT < 10000000"
  expect_zero "存量档案 id 全部小于 1000 万（与新账号 id 不重叠）" "SELECT COUNT(*) FROM member WHERE id >= 10000000"
  expect_zero "notification 每行恰好一个收件人（账号 / 档案二选一非空）" \
    "SELECT COUNT(*) FROM notification WHERE (recipient_account_id IS NULL) = (recipient_profile_id IS NULL)"
  expect_zero "夹具通知（expiry）回填为档案级收件人" \
    "SELECT COUNT(*) FROM notification WHERE type = 'expiry' AND (recipient_profile_id IS NULL OR recipient_account_id IS NOT NULL)"
  expect_zero "feedback_reply / community 通知回填为账号级收件人" \
    "SELECT COUNT(*) FROM notification WHERE type IN ('feedback_reply', 'community') AND (recipient_account_id IS NULL OR recipient_profile_id IS NOT NULL)"
}

# ------------------------------------------------------------------ 主流程
echo "== multi-tenant-family 迁移演练：模式 ${MODE}，库 ${DB_NAME}，工作目录 ${WORK} =="
if [ "${MODE}" = "--from-scratch" ]; then prepare_from_scratch; else prepare_existing; fi

info "迁移前结构快照"
snapshot "${WORK}/schema_before.tsv"

for f in "${TARGET_FILES[@]}"; do apply_file "${f}"; done
info "二次执行 V75–V79（幂等性检查）"
for f in "${TARGET_FILES[@]}"; do
  mysql_db < "${SQL_DIR}/${f}" > "${WORK}/reapply_${f}.log" || fail "${f} 重复执行失败（非幂等）"
done

verify_check_sql
v77_assertions

info "执行回滚脚本 ${ROLLBACK_SQL}"
mysql_db < "${ROLLBACK_SQL}" > "${WORK}/rollback.log" || die "回滚脚本执行失败（输出见 ${WORK}/rollback.log）"
expect_zero "回滚后迁移登记已清除" \
  "SELECT COUNT(*) FROM schema_migration_log WHERE filename IN ('V75__account_family.sql',
     'V76__tenant_family_columns.sql', 'V77__tenant_data_migration.sql', 'V78__tenant_family_not_null.sql',
     'V79__account_merge_log.sql')"

info "回滚后结构快照对比"
snapshot "${WORK}/schema_after_rollback.tsv"
if diff -u "${WORK}/schema_before.tsv" "${WORK}/schema_after_rollback.tsv" > "${WORK}/schema.diff"; then
  pass "回滚后结构与迁移前一致（表 / 列 / 索引）"
else
  fail "回滚后结构与迁移前不一致，差异见 ${WORK}/schema.diff："
  head -n 40 "${WORK}/schema.diff" | sed 's/^/      /'
fi

info "回滚验证完毕，重新执行 V75–V79 并按序执行后续迁移（V80+）"
for f in "${TARGET_FILES[@]}"; do apply_file "${f}"; done
for name in $(all_migrations); do
  v=$((10#$(version_of "${name}")))
  [ "${v}" -gt 79 ] || continue
  if [ "$(q "SELECT COUNT(*) FROM schema_migration_log WHERE filename = '${name}'")" = "0" ]; then
    apply_file "${name}"
    pass "后续迁移 ${name} 执行成功"
  fi
done
expect_zero "重新迁移后校验项仍全部为 0" \
  "SELECT COUNT(*) FROM (SELECT 1 FROM member WHERE family_id IS NULL UNION ALL SELECT 1 FROM menu WHERE family_id IS NULL) x"
v91_assertions

echo
if [ "${FAILURES}" -eq 0 ]; then
  echo "✅ 演练通过（回滚后结构与迁移前一致；最终库 ${DB_NAME} 已重新迁移到最新；日志 ${WORK}）"
else
  echo "❌ 演练失败：${FAILURES} 项断言未通过（日志 ${WORK}）"
  exit 1
fi
