-- ============================================================
-- V80: 聚餐邀请凭证有效期 + 撤销（multi-tenant-family E.3）
-- 背景：多家庭后，邀请凭证（token / 口令）是非本家用户进入某个食集聚餐的唯一入口，必须可过期、可撤销。
-- 存量邀请统一补 7 天有效期（从部署时刻起算）；之后每次生成 / 刷新重新计时并重置 revoked。
-- 幂等：按列是否存在判断。回滚：sql/rollback/V80_menu_invite_expiry.sql
-- ============================================================
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_invite' AND COLUMN_NAME = 'expires_at');
SET @s := IF(@c = 0, 'ALTER TABLE menu_invite ADD COLUMN expires_at DATETIME NULL COMMENT ''凭证有效期截止；空=已过期''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_invite' AND COLUMN_NAME = 'revoked');
SET @s := IF(@c = 0, 'ALTER TABLE menu_invite ADD COLUMN revoked TINYINT NOT NULL DEFAULT 0 COMMENT ''1=已撤销''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE menu_invite SET expires_at = DATE_ADD(NOW(), INTERVAL 7 DAY) WHERE expires_at IS NULL;
