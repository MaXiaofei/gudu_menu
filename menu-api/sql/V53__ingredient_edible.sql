-- ============================================================
-- V53__ingredient_edible.sql
-- 咕嘟小食单：食材食用属性（原型 pantry-ingredient.html）
--
-- edible: 1=食用（参与营养计算） 2=饮料零食 3=生活用品（跳过营养计算）
-- 回填：采购品类=饮料 → 饮料零食；其余默认食用。
-- 幂等：INFORMATION_SCHEMA 检查列已存在则跳过（deploy.sh 每次部署自动跑）。
-- ============================================================

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient' AND COLUMN_NAME = 'edible');
SET @s := IF(@c = 0,
    'ALTER TABLE ingredient ADD COLUMN edible TINYINT NOT NULL DEFAULT 1 COMMENT ''食用属性:1食用/2饮料零食/3生活用品''',
    'SELECT ''exists''');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填：饮料分类 → 饮料零食（幂等 UPDATE）
UPDATE ingredient i
JOIN sys_dict d ON d.id = i.purchase_category_id AND d.dict_group = 'purchase_category' AND d.name = '饮料'
SET i.edible = 2
WHERE i.edible = 1;
