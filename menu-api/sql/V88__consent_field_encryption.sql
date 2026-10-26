-- ============================================================
-- V88: 敏感信息单独同意 + 字段级加密（mini-release-compliance B.2 / B.3）
--
-- 1) consent_record：同意 / 撤回记录（只追加）。B.2 用 doc_type = sensitive_health / child_info；
--    B.1 协议同意合入时复用本表（同名同结构）。
-- 2) member.health_consent_account_id：健康档案由哪个账号提供并单独同意（撤回同意时据此删除）。
-- 3) 字段加密列改造（密文 = "ENC:v<版本>:<base64>"，见 FieldCipher）：
--    - member.health_profile JSON → TEXT（密文不是合法 JSON；存量 JSON 文本原样保留，应用兼容读明文）；
--    - member.phone（旧列）、account.phone 加宽到 VARCHAR(255) 容纳密文；
--    - account.phone_hash：手机号 HMAC-SHA256 盲索引（hex 64 位）+ 唯一索引，用于唯一约束与按手机号登录。
--      uk_account_phone 保留（密文每次随机，不会误冲突；对未迁移的明文行仍有效）。
-- 存量数据加密不在 SQL 里做（MySQL 无 AES-GCM）：部署后执行一次
--   java -jar menu-api.jar --spring.main.web-application-type=none --gudu.field-crypto.migrate=encrypt
-- 幂等：information_schema 判断，可重复执行。回滚：sql/rollback/V88-V89_privacy.sql（须先跑 migrate=decrypt）。
-- ============================================================

CREATE TABLE IF NOT EXISTS consent_record (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  account_id  BIGINT       NOT NULL COMMENT '作出同意 / 撤回的登录账号 account.id',
  doc_type    VARCHAR(32)  NOT NULL COMMENT 'sensitive_health / child_info（B.1：terms / privacy）',
  doc_version VARCHAR(32)  NOT NULL COMMENT '同意时的文本版本',
  granted     TINYINT      NOT NULL COMMENT '1 同意 / 0 撤回',
  channel     VARCHAR(16)  NULL     COMMENT '来源端：mp-weixin / app / web',
  client_ip   VARCHAR(64)  NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_consent_account_type (account_id, doc_type, created_at)
) COMMENT '同意记录（只追加；当前状态 = 最新一条）';

-- member.health_consent_account_id
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND COLUMN_NAME = 'health_consent_account_id');
SET @s := IF(@c = 0, 'ALTER TABLE member ADD COLUMN health_consent_account_id BIGINT NULL COMMENT ''提供健康档案并单独同意的账号（B.2）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND INDEX_NAME = 'idx_member_health_consent');
SET @s := IF(@c = 0, 'ALTER TABLE member ADD INDEX idx_member_health_consent (health_consent_account_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- member.health_profile JSON → TEXT
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND COLUMN_NAME = 'health_profile' AND DATA_TYPE = 'json');
SET @s := IF(@c > 0, 'ALTER TABLE member MODIFY COLUMN health_profile TEXT NULL COMMENT ''健康档案 JSON，AES-GCM 加密存储（B.3）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- member.phone（旧列）加宽
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND COLUMN_NAME = 'phone' AND CHARACTER_MAXIMUM_LENGTH < 255);
SET @s := IF(@c > 0, 'ALTER TABLE member MODIFY COLUMN phone VARCHAR(255) NULL COMMENT ''【旧列】登录名；11 位手机号 AES-GCM 加密存储（B.3）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- account.phone 加宽 + phone_hash
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND COLUMN_NAME = 'phone' AND CHARACTER_MAXIMUM_LENGTH < 255);
SET @s := IF(@c > 0, 'ALTER TABLE account MODIFY COLUMN phone VARCHAR(255) NULL COMMENT ''登录手机号，AES-GCM 加密存储（B.3）；注销后置空''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND COLUMN_NAME = 'phone_hash');
SET @s := IF(@c = 0, 'ALTER TABLE account ADD COLUMN phone_hash CHAR(64) NULL COMMENT ''手机号 HMAC-SHA256 盲索引（唯一 / 登录查询）'' AFTER phone', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND INDEX_NAME = 'uk_account_phone_hash');
SET @s := IF(@c = 0, 'ALTER TABLE account ADD UNIQUE INDEX uk_account_phone_hash (phone_hash)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
