-- ============================================================
-- V77: 存量数据迁入多家庭模型（multi-tenant-family D.3）
--
-- 1) 默认家庭「咕嘟之家」（id=1）；厨房圈演示作者占位家庭（id=2，DISSOLVED，任何人不可访问）；
-- 2) member 中有登录凭证（password_hash / openid）且未删除的行 → account（id 沿用 member.id，
--    原 BCrypt 哈希不变，密码不变；wx_ 占位手机号置空；is_admin=1 → SUPER_ADMIN）；
-- 3) 归属：
--    - 「纯微信自动注册」账号（有 openid、无密码、无健康档案、非超管）——这些是 wx-login 自动建号的陌生用户，
--      旧版 MemberService.household 也不把他们算作家庭成员 → 各自独立建「我的家」并成为 OWNER；
--    - 其余账号（超管、超管代建的手机号账号、已建健康档案的账号）→ 默认家庭；
--      超管中 id 最小者为 OWNER，其余超管为 ADMIN，其他为 MEMBER；
--    - 全部 member 档案 → 默认家庭（演示作者 cm_ → 占位家庭 2；微信自动注册者的档案 → 其自己的家）；
--      有账号的档案写 account_id；
-- 4) 家庭域表 family_id 回填为默认家庭；dish.family_id 保持 NULL（全部进入公共菜谱库）；
-- 5) E.8：不做任何账号自动合并。
-- 偏离 design §5「现有成员全部加入默认家庭」之处见 tasks.md D.3 备注（避免陌生微信用户看到默认家庭数据）。
-- 幂等：全部 NOT EXISTS / IS NULL 守卫，可重复执行。
-- ============================================================

-- 1) 默认家庭 + 演示作者占位家庭
INSERT INTO family(id, name, owner_account_id, status, version, created_at)
SELECT 1, '咕嘟之家', NULL, 'ACTIVE', 0, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM family WHERE id = 1);
INSERT INTO family(id, name, owner_account_id, status, version, created_at, dissolved_at)
SELECT 2, '厨房圈演示作者', NULL, 'DISSOLVED', 0, NOW(), NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM family WHERE id = 2);

-- 2) 账号（id 沿用 member.id：旧 token / 旧数据里按 member_id 归属的个人数据无需改写）
INSERT INTO account(id, phone, wx_openid, password_hash, nickname, avatar_url, platform_role, status, created_at)
SELECT m.id,
       CASE WHEN m.phone IS NULL OR m.phone LIKE 'wx\_%' OR m.phone LIKE 'cm\_%' THEN NULL ELSE m.phone END,
       m.openid, m.password_hash, LEFT(m.name, 32), m.avatar_url,
       CASE WHEN m.is_admin = 1 THEN 'SUPER_ADMIN' ELSE 'USER' END, 'ACTIVE', COALESCE(m.create_time, NOW())
FROM member m
WHERE m.deleted = 0 AND (m.password_hash IS NOT NULL OR m.openid IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM account a WHERE a.id = m.id);

-- 新账号 id 从 max(member.id, account.id)+1 起，避免与存量档案 id 重号造成误读
SET @next := (SELECT GREATEST(COALESCE((SELECT MAX(id) FROM account), 0), COALESCE((SELECT MAX(id) FROM member), 0)) + 1);
SET @s := CONCAT('ALTER TABLE account AUTO_INCREMENT = ', @next);
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 家庭 id 从 1000 起（1、2 已占用；留出固定 id 空间）
SET @fnext := (SELECT GREATEST(COALESCE((SELECT MAX(id) FROM family), 0) + 1, 1000));
SET @s := CONCAT('ALTER TABLE family AUTO_INCREMENT = ', @fnext);
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3a) 纯微信自动注册账号 → 各自的「我的家」
INSERT INTO family(name, owner_account_id, status, version, created_at)
SELECT '我的家', a.id, 'ACTIVE', 0, NOW()
FROM account a JOIN member m ON m.id = a.id
WHERE m.openid IS NOT NULL AND m.password_hash IS NULL AND m.health_profile IS NULL AND COALESCE(m.is_admin, 0) = 0
  AND NOT EXISTS (SELECT 1 FROM family f WHERE f.owner_account_id = a.id)
  AND NOT EXISTS (SELECT 1 FROM family_membership fm WHERE fm.account_id = a.id);
INSERT INTO family_membership(family_id, account_id, role, joined_at)
SELECT f.id, f.owner_account_id, 'OWNER', f.created_at
FROM family f
WHERE f.id > 2 AND f.owner_account_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM family_membership fm WHERE fm.family_id = f.id AND fm.account_id = f.owner_account_id);
UPDATE member m JOIN family f ON f.owner_account_id = m.id AND f.id > 2
SET m.family_id = f.id, m.account_id = m.id
WHERE m.family_id IS NULL;

-- 3b) 其余账号 → 默认家庭
INSERT INTO family_membership(family_id, account_id, role, joined_at)
SELECT 1, a.id, 'MEMBER', a.created_at
FROM account a
WHERE NOT EXISTS (SELECT 1 FROM family_membership fm WHERE fm.account_id = a.id);

-- 默认家庭恰好一个 OWNER：已有则不动；否则取 id 最小的超管（没有超管则取 id 最小的成员）
SET @owner := (SELECT account_id FROM family_membership WHERE family_id = 1 AND role = 'OWNER' LIMIT 1);
SET @cand := (SELECT MIN(fm.account_id) FROM family_membership fm JOIN account a ON a.id = fm.account_id
              WHERE fm.family_id = 1 AND a.platform_role = 'SUPER_ADMIN');
SET @cand := COALESCE(@cand, (SELECT MIN(account_id) FROM family_membership WHERE family_id = 1));
UPDATE family_membership SET role = 'OWNER' WHERE @owner IS NULL AND family_id = 1 AND account_id = @cand;
UPDATE family_membership fm JOIN account a ON a.id = fm.account_id
SET fm.role = 'ADMIN'
WHERE fm.family_id = 1 AND fm.role = 'MEMBER' AND a.platform_role = 'SUPER_ADMIN';
UPDATE family f
SET f.owner_account_id = (SELECT m.account_id FROM family_membership m WHERE m.family_id = f.id AND m.role = 'OWNER' LIMIT 1)
WHERE f.owner_account_id IS NULL AND f.status = 'ACTIVE';

-- 上次使用的家庭
UPDATE account a JOIN family_membership fm ON fm.account_id = a.id
SET a.last_family_id = fm.family_id
WHERE a.last_family_id IS NULL;

-- 成员档案：演示作者 → 占位家庭 2；其余 → 默认家庭；有账号的写 account_id
UPDATE member SET family_id = 2 WHERE family_id IS NULL AND phone LIKE 'cm\_%';
UPDATE member SET family_id = 1 WHERE family_id IS NULL;
UPDATE member m JOIN account a ON a.id = m.id
SET m.account_id = a.id
WHERE m.account_id IS NULL AND m.family_id = 1 AND m.deleted = 0;

-- 4) 家庭域表回填默认家庭（dish 不回填：存量菜谱进入公共库）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_dish' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu_dish SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_prep_status' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu_prep_status SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_activity' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu_activity SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_invite' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu_invite SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_join' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu_join SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_cook_usage' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu_cook_usage SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_template' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu_template SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient_stock' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE ingredient_stock SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pantry' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE pantry SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pantry_change_log' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE pantry_change_log SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_log' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE stock_log SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_list' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE shopping_list SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_item' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE shopping_item SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooking_record' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE cooking_record SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'favorite' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE favorite SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_log' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE daily_log SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_log_item' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE daily_log_item SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE review SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review_score' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE review_score SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_draft' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE dish_draft SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_history' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE dish_history SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE meal_plan SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_item' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE meal_plan_item SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_draft' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE meal_plan_draft SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_slot' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE meal_plan_slot SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE notification SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
