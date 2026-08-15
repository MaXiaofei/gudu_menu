-- V50: member 加 openid 列（微信小程序静默登录，2026-08-15）
-- 背景：新用户零门槛进入小程序——wx.login 静默换 openid，按 openid 查/建 member 账号
--（V29 合并后 member 同时承载账号与就餐成员，openid 挂 member 即两端账号体系打通的前提）。
-- 幂等：information_schema 判断列存在才加。

SET @ col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND COLUMN_NAME = 'openid'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE member ADD COLUMN openid VARCHAR(64) NULL COMMENT ''微信 openid（小程序静默登录用）'' , ADD UNIQUE KEY uk_openid (openid)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
