-- ============================================================
-- V66: member 加 avatar_url（feature-gaps-closure G7 个人资料编辑）
-- 只存本站上传地址（/gudu/uploads/…），应用层校验。幂等：information_schema 判断。
-- ============================================================
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND COLUMN_NAME = 'avatar_url');
SET @s := IF(@c = 0,
  'ALTER TABLE member ADD COLUMN avatar_url VARCHAR(255) NULL COMMENT ''头像（本站 uploads 地址）'' AFTER name',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
