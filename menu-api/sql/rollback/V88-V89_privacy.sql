-- ============================================================
-- 回滚：mini-release-compliance V88–V89（同意记录 / 字段加密 / 保存期限索引 / dish.created_by）
--
-- 执行顺序（必须）：
--   1) 停止新版本应用；
--   2) 用新版本 jar 把密文还原为明文（一次性进程，跑完自动退出）：
--        java -jar menu-api.jar --spring.profiles.active=prod --spring.main.web-application-type=none \
--             --gudu.field-crypto.migrate=decrypt
--      （需与加密时相同的 GUDU_FIELD_CRYPTO_KEYS，含全部历史版本）
--   3) 执行本脚本；第一条 SELECT 的 3 个计数必须全为 0，否则回到第 2 步；
--   4) 部署旧版本应用。
-- 保护：若仍有密文，MODIFY health_profile 回 JSON / 收窄 phone 会因数据非法报错中止，不会截断数据。
-- consent_record 不删除（同意 / 撤回记录是合规证据，旧代码不读该表，保留无副作用）。
-- 幂等：information_schema 判断。
-- ============================================================
SELECT
  (SELECT COUNT(*) FROM account WHERE phone LIKE 'ENC:v%') AS account_phone_encrypted,
  (SELECT COUNT(*) FROM member WHERE phone LIKE 'ENC:v%') AS member_phone_encrypted,
  (SELECT COUNT(*) FROM member WHERE health_profile LIKE 'ENC:v%') AS member_health_encrypted;

-- V89：dish.created_by 与清理索引
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND INDEX_NAME = 'idx_dish_created_by');
SET @s := IF(@c > 0, 'ALTER TABLE dish DROP INDEX idx_dish_created_by', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'created_by');
SET @s := IF(@c > 0, 'ALTER TABLE dish DROP COLUMN created_by', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback' AND INDEX_NAME = 'idx_feedback_status_update');
SET @s := IF(@c > 0, 'ALTER TABLE feedback DROP INDEX idx_feedback_status_update', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_session' AND INDEX_NAME = 'idx_agent_session_updated');
SET @s := IF(@c > 0, 'ALTER TABLE agent_session DROP INDEX idx_agent_session_updated', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_message' AND INDEX_NAME = 'idx_agent_message_time');
SET @s := IF(@c > 0, 'ALTER TABLE agent_message DROP INDEX idx_agent_message_time', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_call_log' AND INDEX_NAME = 'idx_aicall_time');
SET @s := IF(@c > 0, 'ALTER TABLE ai_call_log DROP INDEX idx_aicall_time', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- V88：account.phone_hash / phone 宽度
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND INDEX_NAME = 'uk_account_phone_hash');
SET @s := IF(@c > 0, 'ALTER TABLE account DROP INDEX uk_account_phone_hash', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND COLUMN_NAME = 'phone_hash');
SET @s := IF(@c > 0, 'ALTER TABLE account DROP COLUMN phone_hash', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND COLUMN_NAME = 'phone' AND CHARACTER_MAXIMUM_LENGTH > 32);
SET @s := IF(@c > 0, 'ALTER TABLE account MODIFY COLUMN phone VARCHAR(32) NULL COMMENT ''登录手机号（历史超管登录名 admin 亦存于此）；注销后置空''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- V88：member.phone 宽度 / health_profile 回 JSON / health_consent_account_id
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND COLUMN_NAME = 'phone' AND CHARACTER_MAXIMUM_LENGTH > 20);
SET @s := IF(@c > 0, 'ALTER TABLE member MODIFY COLUMN phone VARCHAR(20) NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND COLUMN_NAME = 'health_profile' AND DATA_TYPE <> 'json');
SET @s := IF(@c > 0, 'ALTER TABLE member MODIFY COLUMN health_profile JSON NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND INDEX_NAME = 'idx_member_health_consent');
SET @s := IF(@c > 0, 'ALTER TABLE member DROP INDEX idx_member_health_consent', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND COLUMN_NAME = 'health_consent_account_id');
SET @s := IF(@c > 0, 'ALTER TABLE member DROP COLUMN health_consent_account_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 迁移登记（deploy.sh）：允许重新部署时再次执行 V88/V89
DELETE FROM schema_migration_log WHERE filename IN ('V88__consent_field_encryption.sql', 'V89__data_retention_export.sql');
