-- ============================================================
-- V87: 图片违规人工复核（mini-release-compliance F.3）
-- content_media_check 加复核字段：review_status = NULL（未复核）/ CONFIRMED（确认违规）/ RESTORED（误判已恢复）。
-- RESTORED 后该图片不再视为违规（隔离文件已还原、头像 / 帖子已恢复）。
-- 幂等：information_schema 判断列是否存在。
-- ============================================================
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'content_media_check' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'content_media_check' AND c.COLUMN_NAME = 'review_status'));
SET @s := IF(@c > 0, 'ALTER TABLE content_media_check ADD COLUMN review_status VARCHAR(16) NULL COMMENT ''人工复核：CONFIRMED / RESTORED''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'content_media_check' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'content_media_check' AND c.COLUMN_NAME = 'reviewed_by'));
SET @s := IF(@c > 0, 'ALTER TABLE content_media_check ADD COLUMN reviewed_by BIGINT NULL COMMENT ''复核人 account.id''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'content_media_check' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'content_media_check' AND c.COLUMN_NAME = 'reviewed_at'));
SET @s := IF(@c > 0, 'ALTER TABLE content_media_check ADD COLUMN reviewed_at DATETIME NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'content_media_check' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'content_media_check' AND s.INDEX_NAME = 'idx_content_media_check_status'));
SET @s := IF(@c > 0, 'ALTER TABLE content_media_check ADD INDEX idx_content_media_check_status (status, create_time)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
