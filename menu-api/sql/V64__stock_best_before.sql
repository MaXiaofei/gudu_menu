-- ============================================================
-- V64: 余量保质期 + 品类默认保质天数 + 旧 pantry 批次保质期迁入三档库存（feature-gaps-closure G2/G6）
--
-- 1) ingredient_stock 加 best_before（保质期）/ opened_at（开封时间），三档库存成为唯一库存模型；
-- 2) sys_dict 加 shelf_life_days（仅 purchase_category 使用），初始化各品类默认保质天数；
-- 3) 旧 pantry 批次表 → ingredient_stock.best_before：每食材取「最早的未过期」到期日，
--    只回填已建档且 best_before 为空、档位不是 NONE 的行（不新建档：用户删过档的食材不复活）。
--    pantry 表自此只读冻结，下一版本删除。
-- 幂等：列/索引走 information_schema 判断；数据回填只动 NULL 值，可重复执行。
-- ============================================================

-- 1) ingredient_stock.best_before
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient_stock' AND COLUMN_NAME = 'best_before');
SET @s := IF(@c = 0,
  'ALTER TABLE ingredient_stock ADD COLUMN best_before DATE NULL COMMENT ''保质期（今天到期 daysLeft=0）'' AFTER level',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1b) ingredient_stock.opened_at
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient_stock' AND COLUMN_NAME = 'opened_at');
SET @s := IF(@c = 0,
  'ALTER TABLE ingredient_stock ADD COLUMN opened_at DATETIME NULL COMMENT ''开封/首次用了一些的时间'' AFTER best_before',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1c) 临期查询索引
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient_stock' AND INDEX_NAME = 'idx_stock_best_before');
SET @s := IF(@c = 0, 'ALTER TABLE ingredient_stock ADD INDEX idx_stock_best_before (best_before)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) sys_dict.shelf_life_days
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dict' AND COLUMN_NAME = 'shelf_life_days');
SET @s := IF(@c = 0,
  'ALTER TABLE sys_dict ADD COLUMN shelf_life_days INT NULL COMMENT ''默认保质天数（purchase_category 用）''',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2b) 品类默认保质天数（只填未配置的，运营后续可改；与 ShelfLifePolicy 兜底表一致）
UPDATE sys_dict d
JOIN (
  SELECT '蔬菜' AS name, 5 AS days UNION ALL SELECT '水果', 7 UNION ALL SELECT '畜禽肉', 3
  UNION ALL SELECT '水产海鲜', 2 UNION ALL SELECT '蛋类', 30 UNION ALL SELECT '豆制品', 3
  UNION ALL SELECT '乳制品', 7 UNION ALL SELECT '调味料', 180 UNION ALL SELECT '谷物主食', 180
  UNION ALL SELECT '坚果', 90 UNION ALL SELECT '干菌藻', 180 UNION ALL SELECT '速冻', 90
  UNION ALL SELECT '饮料', 180
) v ON v.name = d.name
SET d.shelf_life_days = v.days
WHERE d.dict_group = 'purchase_category' AND d.shelf_life_days IS NULL;

-- 3) 旧 pantry 批次保质期 → ingredient_stock（每食材最早的未过期到期日）
UPDATE ingredient_stock s
JOIN (
  SELECT p.ingredient_id, MIN(p.expire_date) AS bb
  FROM pantry p
  WHERE p.deleted = 0 AND p.expire_date IS NOT NULL AND p.expire_date >= CURDATE()
  GROUP BY p.ingredient_id
) e ON e.ingredient_id = s.ingredient_id
SET s.best_before = e.bb
WHERE s.best_before IS NULL AND s.level <> 'NONE';
