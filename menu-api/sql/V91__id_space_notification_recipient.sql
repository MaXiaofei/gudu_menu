-- ============================================================
-- V91: 账号 id / 成员档案 id 取值范围错开 + 通知收件人拆列
--
-- 背景：多家庭改造后有两种「人」的 id——账号（account.id = 登录 loginId）与成员档案（member.id）。
-- 存量账号沿用旧 member.id（V77），之后新账号、新档案各自自增，会撞号：账号 120 与档案 120 可能是两个人。
-- 按档案归属的表（做菜记录 / 点评 / AI 对话 / 通知…）若误用账号 id 比较，就会串号。
--
-- 1) 结构性兜底：account 的 AUTO_INCREMENT 抬到 >= 10000000。新账号 id 恒 >= 1000 万，
--    而档案 id（member 自增）远小于此，两者取值范围永不重叠。存量账号 id 与本人旧 member.id 相同（同一个人），无害。
-- 2) notification 收件人拆两列：recipient_account_id（账号级：反馈回复、厨房圈互动）/
--    recipient_profile_id（档案级：临期、采购），二选一非空。旧 member_id 列保留为可空的遗留列（不再读写）。
--    存量回填：type 为 feedback_reply / community → 账号；其余（expiry / shopping / prep…）→ 档案。
--    （这些类型的写入来源可从代码确认：反馈回复与厨房圈通知发给账号 id，临期 / 采购发给档案 id。
--     type 不在上述两类又无法判定的，按原逻辑保守视为档案级——这类行在存量中只可能来自 V13 起的档案级通知。）
-- 3) notification 不再是家庭域表：账号级消息要跟着人走（旧实现按写入时家庭过滤，管理员回复写在管理员的家庭里，
--    用户在自己的家庭里看不到）。family_id 改可空保留为遗留列。
-- 幂等：全部 information_schema 守卫，可重复执行。
-- ============================================================

-- 1) account 自增起点 >= 1000 万（取 GREATEST(1000 万, 当前自增值, MAX(id)+1)，只升不降）
SET @cur := COALESCE((SELECT AUTO_INCREMENT FROM information_schema.TABLES
                      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account'), 0);
SET @mx := COALESCE((SELECT MAX(id) FROM account), 0) + 1;
SET @next := GREATEST(10000000, @cur, @mx);
SET @s := CONCAT('ALTER TABLE account AUTO_INCREMENT = ', @next);
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) notification 收件人两列
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'recipient_account_id');
SET @s := IF(@c = 0,
  'ALTER TABLE notification ADD COLUMN recipient_account_id BIGINT NULL COMMENT ''收件账号 account.id（与 recipient_profile_id 二选一非空）'' AFTER id',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'recipient_profile_id');
SET @s := IF(@c = 0,
  'ALTER TABLE notification ADD COLUMN recipient_profile_id BIGINT NULL COMMENT ''收件成员档案 member.id（与 recipient_account_id 二选一非空）'' AFTER recipient_account_id',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填（仅处理两列都为空的行；member_id 列还在时才执行）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'member_id');
SET @s := IF(@c > 0,
  'UPDATE notification SET recipient_account_id = member_id
     WHERE recipient_account_id IS NULL AND recipient_profile_id IS NULL AND member_id IS NOT NULL
       AND type IN (''feedback_reply'', ''community'')',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @s := IF(@c > 0,
  'UPDATE notification SET recipient_profile_id = member_id
     WHERE recipient_account_id IS NULL AND recipient_profile_id IS NULL AND member_id IS NOT NULL',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 旧 member_id 列改可空（新写入不再填）；family_id 改可空（不再是家庭域表）
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'member_id' AND IS_NULLABLE = 'NO');
SET @s := IF(@c > 0,
  'ALTER TABLE notification MODIFY COLUMN member_id BIGINT NULL COMMENT ''遗留列（V91 起不再读写；改用 recipient_account_id / recipient_profile_id）''',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'NO');
SET @s := IF(@c > 0,
  'ALTER TABLE notification MODIFY COLUMN family_id BIGINT NULL COMMENT ''遗留列（V91 起 notification 不再是家庭域表，不再写入）''',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 索引：按收件人 + 未读查询
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND INDEX_NAME = 'idx_notif_recipient_account');
SET @s := IF(@c = 0,
  'CREATE INDEX idx_notif_recipient_account ON notification(recipient_account_id, is_read)',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND INDEX_NAME = 'idx_notif_recipient_profile');
SET @s := IF(@c = 0,
  'CREATE INDEX idx_notif_recipient_profile ON notification(recipient_profile_id, is_read)',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
