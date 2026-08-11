-- ============================================================
-- V55__unbind_ingredient_unit.sql
-- 咕嘟小食单：食材库不再绑定单位（产品决策 2026-08）
--
-- 删除链路：
--   1) ingredient_unit_gram 换算表（连同 is_default 默认单位语义）
--   2) ingredient.unit_id（食材默认单位）/ ingredient.price（元/默认单位）
--      / ingredient.low_threshold（V39 库存警戒阈值，按默认单位计，V42 档位化后已成死字段）
--   3) dish.price（单菜手填标价，AI 预算推荐已删）
--
-- 保留（停用不删）：
--   - dish_ingredient.unit_id/amount/unit_name（菜谱用量单位，"2个鸡蛋"仍成立）
--   - dish_ingredient.grams / shopping_item.reference_grams / shopping_item.unit_id
--     / pantry.unit_id —— legacy 列，存量数据不动，代码不再读写，后续版本再清
--   - sys_dict(group=unit) 字典 —— 菜谱用量单位、导入工具（RecipeImporter）仍在用
-- 幂等：INFORMATION_SCHEMA 检查列/表存在（deploy.sh 每次部署自动跑）。
-- ============================================================

-- 1) 换算表整体删除
DROP TABLE IF EXISTS ingredient_unit_gram;

-- 2) ingredient 三列删除（幂等）
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient' AND COLUMN_NAME = 'unit_id');
SET @s := IF(@c = 0,
    'SELECT ''unit_id already dropped''',
    'ALTER TABLE ingredient DROP COLUMN unit_id');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient' AND COLUMN_NAME = 'price');
SET @s := IF(@c = 0,
    'SELECT ''price already dropped''',
    'ALTER TABLE ingredient DROP COLUMN price');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient' AND COLUMN_NAME = 'low_threshold');
SET @s := IF(@c = 0,
    'SELECT ''low_threshold already dropped''',
    'ALTER TABLE ingredient DROP COLUMN low_threshold');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) dish.price 删除（AI 预算推荐一并删，手填标价不再使用）
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'price');
SET @s := IF(@c = 0,
    'SELECT ''dish.price already dropped''',
    'ALTER TABLE dish DROP COLUMN price');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
