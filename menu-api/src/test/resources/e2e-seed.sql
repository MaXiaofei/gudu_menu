-- E2E 种子：每个 @Test 方法前执行（@Sql BEFORE_TEST_METHOD）。
-- 静态种子(user/sys_dict/nutrition_metric/ingredient/ingredient_nutrition/dish/dish_ingredient/menu_template)
--   由 V01-V23 已灌，不在此重置。只清理动态业务表，保证用例间数据隔离。
-- 逻辑删除字段 deleted 的表用物理删（DELETE）彻底清，避免 MP 逻辑删除残留干扰。
DELETE FROM shopping_item;
DELETE FROM shopping_list;
DELETE FROM daily_log_item;
DELETE FROM daily_log;
DELETE FROM notification;
DELETE FROM pantry_change_log;
DELETE FROM pantry;
DELETE FROM meal_plan_item;
DELETE FROM meal_plan;
DELETE FROM review;
DELETE FROM review_score;
DELETE FROM cooking_record;
DELETE FROM favorite;
DELETE FROM menu_dish;
DELETE FROM menu;
DELETE FROM dish_history;
DELETE FROM ai_call_log;

-- ============================================================
-- 静态种子自愈：把「番茄/鸡蛋」规范化到测试常量依赖的固定 id(1/2)，
-- 并修复 dish=1(番茄炒蛋) 的 dish_ingredient 指向。
--
-- 背景：V23 按 name 幂等 DELETE+INSERT 食材，会把 V14 demo 的
--   番茄(id=1)/鸡蛋(id=2) 删掉后重插到新自增 id(如 63/138)，
--   而 dish_ingredient(dish=1) 仍引用旧 id(1/2/16/17/18)，形成孤儿
--   → 营养汇总/采购展开/AI 落库全取不到食材，E2E 三场景失败。
--   本块每次 @Test 前幂等修复，可重复执行、对已规范状态无副作用。
-- 幂等要点：先按 name 清掉漂移番茄/鸡蛋(连同其营养/dish_ingredient)，
--   再清空固定 id(1/2) 的占位行，最后以固定 id 重新插入。
-- ============================================================

-- ---- 番茄 -> id=1 ----
-- 先按 name 删漂移番茄（高自增 id）及其营养/dish_ingredient，避免留孤儿
DELETE inn FROM ingredient_nutrition inn JOIN ingredient i ON i.id = inn.ingredient_id WHERE i.name = '番茄';
DELETE di  FROM dish_ingredient  di  JOIN ingredient i ON i.id = di.ingredient_id  WHERE i.name = '番茄';
DELETE FROM ingredient WHERE name = '番茄';
-- 清空固定 id=1 占位（当前可能为空，安全）及其营养/dish_ingredient
DELETE FROM ingredient_nutrition WHERE ingredient_id = 1;
DELETE FROM dish_ingredient       WHERE ingredient_id = 1;
DELETE FROM ingredient WHERE id = 1;
INSERT INTO ingredient(id, name, unit_id, price, purchase_category_id, purchase_count, usage_count, deleted)
  VALUES (1, '番茄', 20, 5, 24, 0, 0, 0);
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) VALUES
  (1, 1, 19), (1, 2, 0.9), (1, 3, 0.2), (1, 4, 4.0), (1, 5, 2.6), (1, 6, 30);

-- ---- 鸡蛋 -> id=2 ----
DELETE inn FROM ingredient_nutrition inn JOIN ingredient i ON i.id = inn.ingredient_id WHERE i.name = '鸡蛋';
DELETE di  FROM dish_ingredient  di  JOIN ingredient i ON i.id = di.ingredient_id  WHERE i.name = '鸡蛋';
DELETE FROM ingredient WHERE name = '鸡蛋';
DELETE FROM ingredient_nutrition WHERE ingredient_id = 2;
DELETE FROM dish_ingredient       WHERE ingredient_id = 2;
DELETE FROM ingredient WHERE id = 2;
INSERT INTO ingredient(id, name, unit_id, price, purchase_category_id, purchase_count, usage_count, deleted)
  VALUES (2, '鸡蛋', 20, 8, 27, 0, 0, 0);
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) VALUES
  (2, 1, 144), (2, 2, 13.3), (2, 3, 8.8), (2, 4, 2.8), (2, 5, 1.5), (2, 6, 30);

-- ---- dish=1(番茄炒蛋) 食材挂载：清掉全部（含孤儿 16/17/18），挂 番茄(300g)+鸡蛋(180g) ----
DELETE FROM dish_ingredient WHERE dish_id = 1;
INSERT INTO dish_ingredient(dish_id, ingredient_id, amount) VALUES (1, 1, 300.00), (1, 2, 180.00);

-- ---- 兜底：清 ingredient_nutrition 里任何 ingredient 已不存在的孤儿行 ----
DELETE inn FROM ingredient_nutrition inn LEFT JOIN ingredient i ON i.id = inn.ingredient_id WHERE i.id IS NULL;

-- 重置自增，便于断言固定 id（可选；测试用返回 id 不依赖固定值，故保留默认即可）。

-- ============================================================
-- V29 合并兜底：给 member 1(张爸爸/掌勺) 补登录账号 phone='13800000001' / 密码 'chef123'
-- 用于「手机号登录」E2E 场景。每测试前幂等重置（测试库专用，覆盖安全）。
-- BCrypt('chef123') 固定哈希，避免每条测试重新 encode。
-- ============================================================
UPDATE member SET phone = '13800000001',
  password_hash = '$2a$10$8fMxXFI3W4XzBun3fNpUIuXT4dN9CRvHcw6K7edMJU78705ETwVrK'
WHERE id = 1;
