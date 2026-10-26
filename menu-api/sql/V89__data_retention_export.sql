-- ============================================================
-- V89: 数据保存期限清理 + 导出我的数据（mini-release-compliance C.1 / C.2）
--
-- 1) 清理按时间分批删除（DELETE ... WHERE 时间 < ? LIMIT 500），补时间索引避免全表扫描：
--    ai_call_log(create_time)、agent_message(created_at)、agent_session(updated_at)、feedback(status, update_time)；
--    agent_trace 已有 idx_agent_trace_time。
-- 2) dish.created_by：菜谱创建人账号（导出「本人创建的菜谱」用；V89 之前的存量菜谱为 NULL，无法归属不导出）。
-- 幂等：information_schema 判断，可重复执行。回滚：sql/rollback/V88-V89_privacy.sql。
-- ============================================================

SET @c := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_call_log')
        - (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_call_log' AND INDEX_NAME = 'idx_aicall_time');
SET @s := IF(@c > 0, 'ALTER TABLE ai_call_log ADD INDEX idx_aicall_time (create_time)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_message')
        - (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_message' AND INDEX_NAME = 'idx_agent_message_time');
SET @s := IF(@c > 0, 'ALTER TABLE agent_message ADD INDEX idx_agent_message_time (created_at)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_session')
        - (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_session' AND INDEX_NAME = 'idx_agent_session_updated');
SET @s := IF(@c > 0, 'ALTER TABLE agent_session ADD INDEX idx_agent_session_updated (updated_at)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback')
        - (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback' AND INDEX_NAME = 'idx_feedback_status_update');
SET @s := IF(@c > 0, 'ALTER TABLE feedback ADD INDEX idx_feedback_status_update (status, update_time)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- dish.created_by
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'created_by');
SET @s := IF(@c = 0, 'ALTER TABLE dish ADD COLUMN created_by BIGINT NULL COMMENT ''创建人 account.id（V89；存量为 NULL）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND INDEX_NAME = 'idx_dish_created_by');
SET @s := IF(@c = 0, 'ALTER TABLE dish ADD INDEX idx_dish_created_by (created_by)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
