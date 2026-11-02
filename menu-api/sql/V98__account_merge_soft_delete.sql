SET NAMES utf8mb4;
-- ============================================================
-- V98: 账号合并后，没用的那一条数据做软删除
--
-- 需求：用户合并（微信账号 W 并入手机号账号 P）以后，W 这一条已无用的数据做软删除。
--
-- 设计取舍（account 软删除用 deleted_at，不复用 status）：
--   status=MERGED 表达「为什么不可用」（登录拒绝、合并幂等判断依赖它），deleted_at 表达「这一行已无用、
--   默认不参与任何查询」，两个维度分开：后台可以按「已合并」筛出来审计，普通查询一律 deleted_at IS NULL。
--   保留 id / merged_into / created_at / deactivated_at，供审计和 account_merge_log 关联。
--
-- 本迁移做 4 件事（全部幂等，可重复执行；MySQL 8）：
--   1) account 加 deleted_at 列 + 索引；
--   2) 存量重复档案：W 在合并时被「降为无账号档案」的重复成员 -> 其按档案归属的数据改指 P 的档案 -> 档案软删除；
--   3) 存量空家庭：W 注册时自动建的「我的家」，合并后只剩 P 一人且无任何业务数据 -> 软解散（status=DISSOLVED）；
--   4) 存量被并账号 W：置 deleted_at、清昵称 / 头像 / last_family_id；openid / unionid 已随 P 持有的一并清空。
--
-- 空家庭判定（必须同时满足，保守）：family 为 ACTIVE、名为「我的家」、创建时间与 W 的注册时间相差 <= 60 秒
-- （即 W 注册时自动建的那个）、只有 1 个成员关系且是 P、成员档案全是 P 的（没有宝宝等无账号档案）、
-- P 另有别的 ACTIVE 家庭、且下列业务表在该家庭下都没有未删除的数据：
-- dish / ingredient（家庭私有）/ menu / menu_template / meal_plan / meal_plan_draft / ingredient_stock /
-- pantry / shopping_list / cooking_record / daily_log / review / favorite / dish_draft。
--
-- 重复档案判定：member 里 account_id 为空、未删除、名字为「微信用户」或等于 W 昵称，
-- 且同一家庭里存在 P 的档案（account_id = P）。
--
-- 执行前（建议先备份，并先跑下面三条只读检查，核对影响范围）：
--   SELECT * FROM account WHERE status = 'MERGED';
--   SELECT m.id, m.family_id, m.name, l.source_id, l.target_id FROM account_merge_log l
--     JOIN account w ON w.id = l.source_id AND w.status = 'MERGED'
--     JOIN member m ON m.account_id IS NULL AND m.deleted = 0 AND (m.name = '微信用户' OR m.name = w.nickname)
--     JOIN member mp ON mp.family_id = m.family_id AND mp.account_id = l.target_id AND mp.deleted = 0;
--   SELECT f.* FROM family f JOIN family_membership fm ON fm.family_id = f.id JOIN account_merge_log l ON l.target_id = fm.account_id
--     JOIN account w ON w.id = l.source_id WHERE f.status = 'ACTIVE' AND f.name = '我的家'
--       AND ABS(TIMESTAMPDIFF(SECOND, f.created_at, w.created_at)) <= 60;
--
-- ============================================================
-- 撤回（回滚）方式
--   完整反向脚本：sql/rollback/V98_account_merge_soft_delete.sql（等价于下面 a~d，且只撤回本迁移置上的标记）：
--   a) 恢复账号：UPDATE account SET deleted_at = NULL WHERE status = 'MERGED';
--      昵称 / 头像在软删除时已清空（属于「清掉无用个人信息」，不可从库内恢复），如需原值从迁移前的备份还原：
--      UPDATE account a JOIN <备份表> b ON b.id = a.id SET a.nickname = b.nickname, a.avatar_url = b.avatar_url,
--        a.last_family_id = b.last_family_id WHERE a.status = 'MERGED';
--   b) 恢复被软删的重复档案：UPDATE member SET deleted = 0 WHERE id IN (<迁移时 tmp_merge_dup 记录的 dup_id，
--      见 account_merge_soft_delete_log 表>)；改指过去的业务数据不自动改回（它们本来就是同一个人的数据）。
--   c) 恢复被软解散的空家庭：UPDATE family SET status = 'ACTIVE', dissolved_at = NULL
--        WHERE id IN (SELECT family_id FROM account_merge_soft_delete_log WHERE kind = 'FAMILY');
--   d) 最后才可删列（回滚代码后再做）：ALTER TABLE account DROP COLUMN deleted_at;
--   本迁移把 b、c 动过的 id 记在 account_merge_soft_delete_log 表，撤回时按它精确恢复。
-- ============================================================

-- 1) account.deleted_at
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND COLUMN_NAME = 'deleted_at');
SET @s := IF(@c = 0,
  'ALTER TABLE account ADD COLUMN deleted_at DATETIME NULL COMMENT ''软删除时间（V98）：被合并掉的账号非空；默认不参与任何查询'' AFTER deactivated_at',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND INDEX_NAME = 'idx_account_deleted_at');
SET @s := IF(@c = 0, 'ALTER TABLE account ADD INDEX idx_account_deleted_at (deleted_at)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 撤回用的操作记录：本迁移软删除的档案 / 软解散的家庭（幂等：INSERT IGNORE + 唯一键）
CREATE TABLE IF NOT EXISTS account_merge_soft_delete_log (
  id         BIGINT      PRIMARY KEY AUTO_INCREMENT,
  kind       VARCHAR(16) NOT NULL COMMENT 'PROFILE 重复档案 / FAMILY 空家庭',
  ref_id     BIGINT      NOT NULL COMMENT 'member.id 或 family.id',
  target_id  BIGINT      NULL COMMENT 'PROFILE：数据改指到的目标档案 id',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_amsd_kind_ref (kind, ref_id)
) COMMENT '账号合并软删除（V98）操作记录，供撤回';

-- 2) 存量重复档案：先登记，再改指数据，最后软删除
DROP TEMPORARY TABLE IF EXISTS tmp_merge_dup;
CREATE TEMPORARY TABLE tmp_merge_dup (dup_id BIGINT NOT NULL PRIMARY KEY, target_id BIGINT NOT NULL);
INSERT IGNORE INTO tmp_merge_dup(dup_id, target_id)
SELECT m.id, mp.id
FROM account_merge_log l
JOIN account w ON w.id = l.source_id AND w.status = 'MERGED'
JOIN member m ON m.account_id IS NULL AND m.deleted = 0 AND (m.name = '微信用户' OR m.name = w.nickname)
JOIN member mp ON mp.family_id = m.family_id AND mp.account_id = l.target_id AND mp.deleted = 0
WHERE m.id <> mp.id;

INSERT IGNORE INTO account_merge_soft_delete_log(kind, ref_id, target_id)
SELECT 'PROFILE', dup_id, target_id FROM tmp_merge_dup;

-- 收藏有 UNIQUE(member_id, dish_id)：目标已收藏的菜，去掉重复档案的那条
DELETE f FROM favorite f
JOIN tmp_merge_dup d ON f.member_id = d.dup_id
JOIN favorite t ON t.member_id = d.target_id AND t.dish_id = f.dish_id;
UPDATE favorite f JOIN tmp_merge_dup d ON f.member_id = d.dup_id SET f.member_id = d.target_id;
UPDATE cooking_record t JOIN tmp_merge_dup d ON t.member_id = d.dup_id SET t.member_id = d.target_id;
UPDATE review t JOIN tmp_merge_dup d ON t.member_id = d.dup_id SET t.member_id = d.target_id;
UPDATE daily_log t JOIN tmp_merge_dup d ON t.member_id = d.dup_id SET t.member_id = d.target_id;
UPDATE menu t JOIN tmp_merge_dup d ON t.target_member_id = d.dup_id SET t.target_member_id = d.target_id;
UPDATE dish_draft t JOIN tmp_merge_dup d ON t.user_id = d.dup_id SET t.user_id = d.target_id;
UPDATE meal_plan_draft t JOIN tmp_merge_dup d ON t.member_id = d.dup_id SET t.member_id = d.target_id;
UPDATE ai_call_log t JOIN tmp_merge_dup d ON t.member_id = d.dup_id SET t.member_id = d.target_id;
UPDATE notification t JOIN tmp_merge_dup d ON t.recipient_profile_id = d.dup_id SET t.recipient_profile_id = d.target_id;
UPDATE agent_session t JOIN tmp_merge_dup d ON t.member_id = d.dup_id SET t.member_id = d.target_id;
UPDATE agent_message t JOIN tmp_merge_dup d ON t.member_id = d.dup_id SET t.member_id = d.target_id;
UPDATE agent_trace t JOIN tmp_merge_dup d ON t.member_id = d.dup_id SET t.member_id = d.target_id;
-- menu_dish.added_by_member_id 混存账号 / 档案 id、仅作留痕、无读取路径（design 9.2），不改。

UPDATE member m JOIN tmp_merge_dup d ON m.id = d.dup_id SET m.deleted = 1;
DROP TEMPORARY TABLE IF EXISTS tmp_merge_dup;

-- 3) 存量空家庭（判定用到 W.created_at / status，须在第 4 步账号软删除之前）
DROP TEMPORARY TABLE IF EXISTS tmp_merge_empty_family;
CREATE TEMPORARY TABLE tmp_merge_empty_family (family_id BIGINT NOT NULL PRIMARY KEY, p_id BIGINT NOT NULL);
INSERT IGNORE INTO tmp_merge_empty_family(family_id, p_id)
SELECT f.id, l.target_id
FROM account_merge_log l
JOIN account w ON w.id = l.source_id AND w.status = 'MERGED'
JOIN family_membership fm ON fm.account_id = l.target_id
JOIN family f ON f.id = fm.family_id AND f.status = 'ACTIVE' AND f.name = '我的家'
WHERE ABS(TIMESTAMPDIFF(SECOND, f.created_at, w.created_at)) <= 60
  AND (SELECT COUNT(*) FROM family_membership x WHERE x.family_id = f.id) = 1
  AND NOT EXISTS (SELECT 1 FROM member m WHERE m.family_id = f.id AND m.deleted = 0
                  AND (m.account_id IS NULL OR m.account_id <> l.target_id))
  AND EXISTS (SELECT 1 FROM family_membership fo JOIN family f2 ON f2.id = fo.family_id AND f2.status = 'ACTIVE'
              WHERE fo.account_id = l.target_id AND fo.family_id <> f.id)
  AND NOT EXISTS (SELECT 1 FROM dish x WHERE x.family_id = f.id AND x.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM ingredient x WHERE x.family_id = f.id AND x.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM menu x WHERE x.family_id = f.id AND x.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM menu_template x WHERE x.family_id = f.id)
  AND NOT EXISTS (SELECT 1 FROM meal_plan x WHERE x.family_id = f.id AND x.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM meal_plan_draft x WHERE x.family_id = f.id)
  AND NOT EXISTS (SELECT 1 FROM ingredient_stock x WHERE x.family_id = f.id)
  AND NOT EXISTS (SELECT 1 FROM pantry x WHERE x.family_id = f.id AND x.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM shopping_list x WHERE x.family_id = f.id AND x.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM cooking_record x WHERE x.family_id = f.id)
  AND NOT EXISTS (SELECT 1 FROM daily_log x WHERE x.family_id = f.id AND x.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM review x WHERE x.family_id = f.id AND x.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM favorite x WHERE x.family_id = f.id)
  AND NOT EXISTS (SELECT 1 FROM dish_draft x WHERE x.family_id = f.id);

INSERT IGNORE INTO account_merge_soft_delete_log(kind, ref_id, target_id)
SELECT 'FAMILY', family_id, p_id FROM tmp_merge_empty_family;

UPDATE family f JOIN tmp_merge_empty_family e ON e.family_id = f.id
SET f.status = 'DISSOLVED', f.dissolved_at = NOW()
WHERE f.status = 'ACTIVE';
-- P 上次使用的家庭若正是被解散的，清空让登录回落到其他家庭
UPDATE account a JOIN tmp_merge_empty_family e ON a.id = e.p_id AND a.last_family_id = e.family_id
SET a.last_family_id = NULL;
DROP TEMPORARY TABLE IF EXISTS tmp_merge_empty_family;

-- 4) 存量被并账号 W：软删除 + 清无用个人信息（id / merged_into / created_at / deactivated_at 保留）
-- 4a) openid / unionid：只清「P 已持有同一 openid」的（确认已转给 P），防止误清
UPDATE account w JOIN account p ON p.id = w.merged_into AND p.wx_openid = w.wx_openid
SET w.wx_openid = NULL, w.wx_unionid = NULL
WHERE w.status = 'MERGED' AND w.wx_openid IS NOT NULL;
-- 4b) 软删除
UPDATE account
SET deleted_at = COALESCE(deactivated_at, NOW()),
    nickname = NULL, avatar_url = NULL, last_family_id = NULL
WHERE status = 'MERGED' AND deleted_at IS NULL;
