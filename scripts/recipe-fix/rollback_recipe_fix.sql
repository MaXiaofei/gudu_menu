-- 撤回下厨房菜谱用料补全（按 recipe_fix_log 逆序还原；可重复执行）
-- 新增食材只删没有被其他地方用到的（用户已拿它记库存/采购/自建菜谱的保留）
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE dish d JOIN recipe_fix_log l ON l.ref_id = d.id AND l.batch = 'xcf-ingredients-20261027' AND l.kind = 'dish_hidden'
SET d.deleted = 0;

UPDATE dish d JOIN recipe_fix_log l ON l.ref_id = d.id AND l.batch = 'xcf-ingredients-20261027' AND l.kind = 'note_cleared'
SET d.note = l.old_value;

DELETE di FROM dish_ingredient di
JOIN recipe_fix_log l ON l.ref_id = di.id AND l.batch = 'xcf-ingredients-20261027' AND l.kind = 'dish_ingredient_added';

DELETE i FROM ingredient i
JOIN recipe_fix_log l ON l.ref_id = i.id AND l.batch = 'xcf-ingredients-20261027' AND l.kind = 'ingredient_added'
WHERE NOT EXISTS (SELECT 1 FROM dish_ingredient x WHERE x.ingredient_id = i.id)
  AND NOT EXISTS (SELECT 1 FROM ingredient_nutrition x WHERE x.ingredient_id = i.id)
  AND NOT EXISTS (SELECT 1 FROM ingredient_stock x WHERE x.ingredient_id = i.id)
  AND NOT EXISTS (SELECT 1 FROM stock_log x WHERE x.ingredient_id = i.id)
  AND NOT EXISTS (SELECT 1 FROM shopping_item x WHERE x.ingredient_id = i.id)
  AND NOT EXISTS (SELECT 1 FROM pantry x WHERE x.ingredient_id = i.id)
  AND NOT EXISTS (SELECT 1 FROM pantry_change_log x WHERE x.ingredient_id = i.id)
  AND NOT EXISTS (SELECT 1 FROM daily_log_item x WHERE x.ingredient_id = i.id)
  AND NOT EXISTS (SELECT 1 FROM menu_cook_usage x WHERE x.ingredient_id = i.id)
  AND NOT EXISTS (SELECT 1 FROM menu_prep_status x WHERE x.ingredient_id = i.id);

DELETE FROM recipe_fix_log WHERE batch = 'xcf-ingredients-20261027';
COMMIT;
