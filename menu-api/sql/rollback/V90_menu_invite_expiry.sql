-- 回滚 V90：删除聚餐邀请的有效期 / 撤销列，并清除迁移登记。
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_invite' AND COLUMN_NAME = 'expires_at');
SET @s := IF(@c > 0, 'ALTER TABLE menu_invite DROP COLUMN expires_at', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_invite' AND COLUMN_NAME = 'revoked');
SET @s := IF(@c > 0, 'ALTER TABLE menu_invite DROP COLUMN revoked', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
DELETE FROM schema_migration_log WHERE filename = 'V90__menu_invite_expiry.sql';
