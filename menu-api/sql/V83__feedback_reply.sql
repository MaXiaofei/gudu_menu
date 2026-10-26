-- ============================================================
-- V83: 意见反馈闭环（mini-release-compliance A.11）
-- feedback 加 type（general 一般 / ai AI 内容 / complaint 投诉举报）、source_ref（来源引用：AI 消息 id、来源页面…）、
--   reply / replied_at / replied_by（运营回复）；状态 NEW → REPLIED（已回复）/ DONE（已处理）。
-- notification 加 ref_id（点击跳转目标，如 feedback_reply 消息指向 feedback.id）。
-- 幂等：information_schema 判断列 / 索引是否存在。
-- ============================================================
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback' AND COLUMN_NAME = 'type');
SET @s := IF(@c = 0,
  'ALTER TABLE feedback ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT ''general'' COMMENT ''general 一般 / ai AI 内容 / complaint 投诉举报'' AFTER member_id',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback' AND COLUMN_NAME = 'source_ref');
SET @s := IF(@c = 0,
  'ALTER TABLE feedback ADD COLUMN source_ref VARCHAR(128) NULL COMMENT ''来源引用（如 agent:消息id、来源页面）'' AFTER type',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback' AND COLUMN_NAME = 'reply');
SET @s := IF(@c = 0,
  'ALTER TABLE feedback ADD COLUMN reply VARCHAR(1000) NULL COMMENT ''运营回复'' AFTER status',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback' AND COLUMN_NAME = 'replied_at');
SET @s := IF(@c = 0,
  'ALTER TABLE feedback ADD COLUMN replied_at DATETIME NULL COMMENT ''回复时间'' AFTER reply',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback' AND COLUMN_NAME = 'replied_by');
SET @s := IF(@c = 0,
  'ALTER TABLE feedback ADD COLUMN replied_by BIGINT NULL COMMENT ''回复人 member.id（超管）'' AFTER replied_at',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 状态注释补 REPLIED（VARCHAR(8) 放得下 'REPLIED'，不改长度）
ALTER TABLE feedback MODIFY COLUMN status VARCHAR(8) NOT NULL DEFAULT 'NEW' COMMENT 'NEW 待处理 / REPLIED 已回复 / DONE 已处理';

SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback' AND INDEX_NAME = 'idx_feedback_type_status');
SET @s := IF(@c = 0,
  'CREATE INDEX idx_feedback_type_status ON feedback(type, status, create_time)',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'ref_id');
SET @s := IF(@c = 0,
  'ALTER TABLE notification ADD COLUMN ref_id VARCHAR(64) NULL COMMENT ''跳转目标 id（feedback_reply → feedback.id）'' AFTER content',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
