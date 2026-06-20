-- ============================================================
-- V28__ingredient_category_fix.sql
-- 烟火小食单：食材库补全无采购分类（purchase_category_id IS NULL OR =0）
--
-- 背景：ingredient 有 18 条 purchase_category_id 为 NULL/0（大米/小米/挂面/燕麦/
--      玉米面/米饭/糯米/荞麦/面粉/馒头/鲜面条/糙米/黑米/藜麦/饺子皮/馄饨皮/紫薯/燕麦片）。
--
-- 策略：按 name 关键词归类，UPDATE...WHERE（幂等，绝不 DELETE）。
--      分类 id 取 sys_dict(purchase_category) 中的对应 name（V27 已补 谷物主食/坚果/干菌藻/速冻）。
--
-- 归类规则：
--   谷物主食：米/面/粉/麦/谷/粥/饭/燕麦/玉米面/挂面/藜麦/糙米/糯米/黑米/小米
--             （饺子皮/馄饨皮 走速冻；紫薯 走蔬菜——根茎类）
--   坚果：    坚果/花生/芝麻/核桃/杏仁/腰果/瓜子/栗子/松子
--   干菌藻：  干/菌/菇/木耳/银耳/海带/紫菜/裙带菜（注意「紫薯」不含「紫菜」）
--   速冻：    饺/馄饨/汤圆/速冻
--
-- 依赖 V27__dict_real.sql 已在 sys_dict 补 谷物主食/坚果/干菌藻/速冻；须先执行 V27。
-- ============================================================

START TRANSACTION;

-- 1) 谷物主食（先于速冻：饺子皮/馄饨皮 含「皮」不含谷物关键词，归速冻；
--    紫薯归蔬菜——含「薯」，不命中谷物/坚果/干菌藻/速冻，需单独兜底）
--    含米面/杂粮/面食制品（馒头/包子/花卷/烙饼/烧饼/窝头/面包/吐司/饼/糕/团）
UPDATE ingredient
SET purchase_category_id = (SELECT id FROM sys_dict WHERE dict_group='purchase_category' AND name='谷物主食')
WHERE (purchase_category_id IS NULL OR purchase_category_id = 0)
  AND name REGEXP '米|面|粉|麦|谷|粥|饭|燕麦|玉米面|挂面|藜麦|糙米|糯米|黑米|小米|馒头|包子|花卷|烙饼|烧饼|窝头|面包|吐司|年糕|米线|米粉|凉皮';

-- 2) 速冻（饺子皮/馄饨皮/汤圆/速冻食品）
UPDATE ingredient
SET purchase_category_id = (SELECT id FROM sys_dict WHERE dict_group='purchase_category' AND name='速冻')
WHERE (purchase_category_id IS NULL OR purchase_category_id = 0)
  AND name REGEXP '饺|馄饨|汤圆|速冻';

-- 3) 坚果（花生/芝麻/核桃/杏仁/腰果/瓜子/栗子/松子等）
UPDATE ingredient
SET purchase_category_id = (SELECT id FROM sys_dict WHERE dict_group='purchase_category' AND name='坚果')
WHERE (purchase_category_id IS NULL OR purchase_category_id = 0)
  AND name REGEXP '坚果|花生|芝麻|核桃|杏仁|腰果|瓜子|栗子|松子';

-- 4) 干菌藻（干香菇/木耳/银耳/海带/紫菜/裙带菜等）
--    注意：REGEXP「干」会误匹配「土豆干」等可接受；「菌/菇」覆盖各种菌菇；
--    「紫菜」需显式列（避免与「紫薯」混淆，二者不在同组，紫薯走蔬菜）
UPDATE ingredient
SET purchase_category_id = (SELECT id FROM sys_dict WHERE dict_group='purchase_category' AND name='干菌藻')
WHERE (purchase_category_id IS NULL OR purchase_category_id = 0)
  AND name REGEXP '干|菌|菇|木耳|银耳|海带|紫菜|裙带菜';

-- 5) 兜底：仍为 NULL/0 的归「蔬菜」（如紫薯/未匹配根茎类）
UPDATE ingredient
SET purchase_category_id = (SELECT id FROM sys_dict WHERE dict_group='purchase_category' AND name='蔬菜')
WHERE (purchase_category_id IS NULL OR purchase_category_id = 0);

COMMIT;
