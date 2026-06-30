-- ============================================================
-- V34 采购清单自定义项可入库：shopping_item 的 ingredient_id / total_amount 放宽约束
-- 背景：V30 加了 custom_name 支持「未命中食材的自定义项」，但
--   - ingredient_id 仍 NOT NULL → 自定义项（ingredientId=NULL）插入失败 500；
--   - total_amount NOT NULL 无默认值 → 自定义项无参考价、不填该字段 → 插入失败。
-- 修复：ingredient_id 改可空；total_amount 加默认 0（自定义项无参考价时合计为 0）。
-- 幂等：按 information_schema 当前状态判断是否需要 MODIFY。
-- 注：唯一键 uk_list_ing_unit(list_id, ingredient_id, unit_id) 在 ingredient_id 为 NULL 时，
--   MySQL 视多条 NULL 互不冲突，自定义项不受其约束，无需调整。
-- ============================================================

-- ingredient_id：NOT NULL → NULL
SET @n := (SELECT IS_NULLABLE FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_item' AND COLUMN_NAME = 'ingredient_id');
SET @sql := IF(@n = 'NO',
  'ALTER TABLE shopping_item MODIFY COLUMN ingredient_id BIGINT NULL',
  'SELECT "ingredient_id already nullable"');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- total_amount：无默认值 → DEFAULT 0
SET @d := (SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_item' AND COLUMN_NAME = 'total_amount');
SET @sql := IF(@d IS NULL,
  'ALTER TABLE shopping_item MODIFY COLUMN total_amount DECIMAL(10,2) NOT NULL DEFAULT 0',
  'SELECT "total_amount already has default"');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
