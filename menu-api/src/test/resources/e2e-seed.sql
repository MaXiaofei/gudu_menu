-- E2E 种子：每个 @Test 方法前执行（@Sql BEFORE_TEST_METHOD）。
-- 静态种子(user/sys_dict/nutrition_metric/ingredient/ingredient_nutrition/dish/dish_ingredient/menu_template)
--   由 V01-V20 已灌，不在此重置。只清理动态业务表，保证用例间数据隔离。
-- 逻辑删除字段 deleted 的表用物理删（DELETE）彻底清，避免 MP 逻辑删除残留干扰。
DELETE FROM shopping_item;
DELETE FROM shopping_list;
DELETE FROM daily_log_item;
DELETE FROM daily_log;
DELETE FROM notification;
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

-- 重置自增，便于断言固定 id（可选；测试用返回 id 不依赖固定值，故保留默认即可）。
