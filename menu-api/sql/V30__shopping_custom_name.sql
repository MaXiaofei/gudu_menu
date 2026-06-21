-- ============================================================
-- V30 采购清单自定义项：shopping_item 加 custom_name VARCHAR(64)
-- 背景：采购清单不强绑菜单/菜品，允许用户手动添加任意食材。
--   命中已有 ingredient → ingredientId 关联 + custom_name 留空；
--   未命中 → ingredientId = NULL，食材名直接存 custom_name。
-- 幂等：information_schema 判列存在则跳过。
-- ============================================================

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_item' AND COLUMN_NAME = 'custom_name');
SET @sql := IF(@col = 0,
  'ALTER TABLE shopping_item ADD COLUMN custom_name VARCHAR(64)',
  'SELECT "custom_name exists"');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
