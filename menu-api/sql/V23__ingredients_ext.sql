-- ============================================================
-- V23__ingredients_ext.sql
-- 咕嘟小食单：扩充常见中国食材库 ~150 种 + 营养 EAV(6 项/食材)
-- 营养数据参考《中国食物成分表》(per 100g)；标注【估】为同品类典型参考值
-- metric_id: 1=calorie(kcal) 2=protein(g) 3=fat(g) 4=carb(g) 5=sugar(g) 6=gi
-- 幂等：按 name 去重插入(不删已有食材)，避免破坏外键
--   注：曾用「by name 删再插」，会把 V14/V15 demo 食材(番茄 id=1、鸡蛋
--   id=2 等)删掉后重插到新自增 id，而 dish_ingredient 仍引用旧 id → 孤儿
--   (见 V24 治标)。现改为「已存在则跳过」：食材用 INSERT...SELECT WHERE
--   NOT EXISTS(name)，营养用 INSERT IGNORE(uk_ing_metric 兜底)。
--   重跑不再 DELETE，保留旧 id + 外键。
-- price=0（已移除价格展示）；UTF-8
-- ============================================================

START TRANSACTION;

-- 1) 批量插入食材(按 name 去重：已存在则跳过，不删不破坏外键)
INSERT INTO ingredient(name, unit_id, price, purchase_category_id)
SELECT v.name, v.unit_id, v.price, v.purchase_category_id
FROM (
  VALUES
  ROW('丝瓜', 20, 0, 24),
  ROW('冬瓜', 20, 0, 24),
  ROW('包菜', 20, 0, 24),
  ROW('南瓜', 20, 0, 24),
  ROW('圆白菜', 20, 0, 24),
  ROW('土豆', 20, 0, 24),
  ROW('大白菜', 20, 0, 24),
  ROW('大葱', 20, 0, 24),
  ROW('大蒜', 20, 0, 24),
  ROW('娃娃菜', 20, 0, 24),
  ROW('小白菜', 20, 0, 24),
  ROW('小葱', 20, 0, 24),
  ROW('山药', 20, 0, 24),
  ROW('平菇', 20, 0, 24),
  ROW('彩椒', 20, 0, 24),
  ROW('扁豆', 20, 0, 24),
  ROW('杏鲍菇', 20, 0, 24),
  ROW('油麦菜', 20, 0, 24),
  ROW('洋葱', 20, 0, 24),
  ROW('海带(鲜)', 20, 0, 24),
  ROW('玉米', 20, 0, 24),
  ROW('生姜', 20, 0, 24),
  ROW('生菜', 20, 0, 24),
  ROW('番茄', 20, 0, 24),
  ROW('白萝卜', 20, 0, 24),
  ROW('空心菜', 20, 0, 24),
  ROW('红薯', 20, 0, 24),
  ROW('红辣椒', 20, 0, 24),
  ROW('胡萝卜', 20, 0, 24),
  ROW('芋头', 20, 0, 24),
  ROW('芹菜', 20, 0, 24),
  ROW('苋菜', 20, 0, 24),
  ROW('苦瓜', 20, 0, 24),
  ROW('茄子', 20, 0, 24),
  ROW('茼蒿', 20, 0, 24),
  ROW('莲藕', 20, 0, 24),
  ROW('莴笋', 20, 0, 24),
  ROW('莴笋叶', 20, 0, 24),
  ROW('菜花', 20, 0, 24),
  ROW('菠菜', 20, 0, 24),
  ROW('西芹', 20, 0, 24),
  ROW('西葫芦', 20, 0, 24),
  ROW('西蓝花', 20, 0, 24),
  ROW('豆角', 20, 0, 24),
  ROW('豌豆', 20, 0, 24),
  ROW('豌豆苗', 20, 0, 24),
  ROW('金针菇', 20, 0, 24),
  ROW('银耳(水发)', 20, 0, 24),
  ROW('青椒', 20, 0, 24),
  ROW('青萝卜', 20, 0, 24),
  ROW('韭菜', 20, 0, 24),
  ROW('鲜香菇', 20, 0, 24),
  ROW('黄瓜', 20, 0, 24),
  ROW('黑木耳(水发)', 20, 0, 24),
  ROW('五花肉', 20, 0, 25),
  ROW('牛肉(瘦)', 20, 0, 25),
  ROW('牛腩', 20, 0, 25),
  ROW('猪大肠', 20, 0, 25),
  ROW('猪心', 20, 0, 25),
  ROW('猪排骨', 20, 0, 25),
  ROW('猪肉(瘦)', 20, 0, 25),
  ROW('猪肉(肥瘦)', 20, 0, 25),
  ROW('猪肝', 20, 0, 25),
  ROW('猪蹄', 20, 0, 25),
  ROW('羊排', 20, 0, 25),
  ROW('羊肉(瘦)', 20, 0, 25),
  ROW('驴肉', 20, 0, 25),
  ROW('鸡爪', 20, 0, 25),
  ROW('鸡翅', 20, 0, 25),
  ROW('鸡肉', 20, 0, 25),
  ROW('鸡胸肉', 20, 0, 25),
  ROW('鸡腿', 20, 0, 25),
  ROW('鸭肉', 20, 0, 25),
  ROW('鸭腿', 20, 0, 25),
  ROW('鹅肉', 20, 0, 25),
  ROW('三文鱼', 20, 0, 26),
  ROW('基围虾', 20, 0, 26),
  ROW('墨鱼', 20, 0, 26),
  ROW('小龙虾', 20, 0, 26),
  ROW('带鱼', 20, 0, 26),
  ROW('扇贝', 20, 0, 26),
  ROW('生蚝', 20, 0, 26),
  ROW('章鱼', 20, 0, 26),
  ROW('紫菜(干)', 20, 0, 26),
  ROW('草鱼', 20, 0, 26),
  ROW('虾仁', 20, 0, 26),
  ROW('蛏子', 20, 0, 26),
  ROW('蛤蜊', 20, 0, 26),
  ROW('螃蟹', 20, 0, 26),
  ROW('鱿鱼', 20, 0, 26),
  ROW('鲈鱼', 20, 0, 26),
  ROW('鲤鱼', 20, 0, 26),
  ROW('鲫鱼', 20, 0, 26),
  ROW('鲳鱼', 20, 0, 26),
  ROW('鳕鱼', 20, 0, 26),
  ROW('黄花鱼', 20, 0, 26),
  ROW('咸鸭蛋', 22, 0, 27),
  ROW('皮蛋', 22, 0, 27),
  ROW('鸡蛋', 22, 0, 27),
  ROW('鸭蛋', 22, 0, 27),
  ROW('鸽蛋', 22, 0, 27),
  ROW('鹌鹑蛋', 22, 0, 27),
  ROW('内酯豆腐', 20, 0, 28),
  ROW('北豆腐', 20, 0, 28),
  ROW('南豆腐', 20, 0, 28),
  ROW('毛豆', 20, 0, 28),
  ROW('红豆', 20, 0, 28),
  ROW('绿豆', 20, 0, 28),
  ROW('腐竹', 20, 0, 28),
  ROW('豆浆', 21, 0, 28),
  ROW('豆腐(北)', 20, 0, 28),
  ROW('豆腐(嫩)', 20, 0, 28),
  ROW('豆腐干', 20, 0, 28),
  ROW('豆腐皮', 20, 0, 28),
  ROW('黄豆', 20, 0, 28),
  ROW('黑豆', 20, 0, 28),
  ROW('奶粉', 20, 0, 29),
  ROW('奶酪', 20, 0, 29),
  ROW('淡奶油', 21, 0, 29),
  ROW('炼乳', 21, 0, 29),
  ROW('牛奶', 21, 0, 29),
  ROW('羊奶', 21, 0, 29),
  ROW('酸奶', 21, 0, 29),
  ROW('黄油', 20, 0, 29),
  ROW('五香粉', 20, 0, 30),
  ROW('八角', 20, 0, 30),
  ROW('冰糖', 20, 0, 30),
  ROW('干辣椒', 20, 0, 30),
  ROW('料酒', 21, 0, 30),
  ROW('桂皮', 20, 0, 30),
  ROW('橄榄油', 21, 0, 30),
  ROW('淀粉', 20, 0, 30),
  ROW('猪油', 20, 0, 30),
  ROW('甜面酱', 20, 0, 30),
  ROW('番茄酱', 20, 0, 30),
  ROW('白糖', 20, 0, 30),
  ROW('白胡椒', 20, 0, 30),
  ROW('红糖', 20, 0, 30),
  ROW('芝麻油', 21, 0, 30),
  ROW('花椒', 20, 0, 30),
  ROW('花生油', 21, 0, 30),
  ROW('菜籽油', 21, 0, 30),
  ROW('蚝油', 21, 0, 30),
  ROW('豆瓣酱', 20, 0, 30),
  ROW('酱油(生抽)', 21, 0, 30),
  ROW('酱油(老抽)', 21, 0, 30),
  ROW('醋', 21, 0, 30),
  ROW('食用油', 21, 0, 30),
  ROW('食盐', 20, 0, 30),
  ROW('黑胡椒', 20, 0, 30),
  ROW('哈密瓜', 22, 0, 31),
  ROW('提子', 22, 0, 31),
  ROW('柚子', 22, 0, 31),
  ROW('桃', 22, 0, 31),
  ROW('梨', 22, 0, 31),
  ROW('榴莲', 22, 0, 31),
  ROW('樱桃', 22, 0, 31),
  ROW('橘子', 22, 0, 31),
  ROW('橙子', 22, 0, 31),
  ROW('火龙果', 22, 0, 31),
  ROW('猕猴桃', 22, 0, 31),
  ROW('石榴', 22, 0, 31),
  ROW('芒果', 22, 0, 31),
  ROW('苹果', 22, 0, 31),
  ROW('草莓', 22, 0, 31),
  ROW('荔枝', 22, 0, 31),
  ROW('菠萝', 22, 0, 31),
  ROW('葡萄', 22, 0, 31),
  ROW('蓝莓', 22, 0, 31),
  ROW('西瓜', 22, 0, 31),
  ROW('香蕉', 22, 0, 31),
  ROW('龙眼', 22, 0, 31),
  ROW('大米', 20, 0, NULL),
  ROW('小米', 20, 0, NULL),
  ROW('挂面', 20, 0, NULL),
  ROW('燕麦', 20, 0, NULL),
  ROW('玉米面', 20, 0, NULL),
  ROW('米饭', 22, 0, NULL),
  ROW('糯米', 20, 0, NULL),
  ROW('荞麦', 20, 0, NULL),
  ROW('面粉', 20, 0, NULL),
  ROW('馒头', 22, 0, NULL),
  ROW('鲜面条', 20, 0, NULL)
) AS v(name, unit_id, price, purchase_category_id)
WHERE NOT EXISTS (SELECT 1 FROM ingredient ex WHERE ex.name = v.name);

-- 3) 每食材 6 项营养（INSERT...SELECT by name）
-- 丝瓜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 20 FROM ingredient WHERE name='丝瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='丝瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='丝瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.2 FROM ingredient WHERE name='丝瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.8 FROM ingredient WHERE name='丝瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 25 FROM ingredient WHERE name='丝瓜';
-- 冬瓜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 11 FROM ingredient WHERE name='冬瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.4 FROM ingredient WHERE name='冬瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='冬瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.6 FROM ingredient WHERE name='冬瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='冬瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 20 FROM ingredient WHERE name='冬瓜';
-- 包菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='包菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='包菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='包菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.6 FROM ingredient WHERE name='包菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.0 FROM ingredient WHERE name='包菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 26 FROM ingredient WHERE name='包菜';
-- 南瓜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='南瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.7 FROM ingredient WHERE name='南瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='南瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.3 FROM ingredient WHERE name='南瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.0 FROM ingredient WHERE name='南瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 75 FROM ingredient WHERE name='南瓜';
-- 圆白菜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='圆白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='圆白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='圆白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.6 FROM ingredient WHERE name='圆白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.0 FROM ingredient WHERE name='圆白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 26 FROM ingredient WHERE name='圆白菜';
-- 土豆
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 76 FROM ingredient WHERE name='土豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.0 FROM ingredient WHERE name='土豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='土豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 17.2 FROM ingredient WHERE name='土豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.8 FROM ingredient WHERE name='土豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 78 FROM ingredient WHERE name='土豆';
-- 大白菜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 17 FROM ingredient WHERE name='大白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='大白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='大白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.2 FROM ingredient WHERE name='大白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='大白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 23 FROM ingredient WHERE name='大白菜';
-- 大葱
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 33 FROM ingredient WHERE name='大葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.7 FROM ingredient WHERE name='大葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='大葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.5 FROM ingredient WHERE name='大葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='大葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='大葱';
-- 大蒜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 126 FROM ingredient WHERE name='大蒜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.5 FROM ingredient WHERE name='大蒜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='大蒜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 27.6 FROM ingredient WHERE name='大蒜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='大蒜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='大蒜';
-- 娃娃菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 13 FROM ingredient WHERE name='娃娃菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='娃娃菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='娃娃菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.6 FROM ingredient WHERE name='娃娃菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='娃娃菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 23 FROM ingredient WHERE name='娃娃菜';
-- 小白菜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 15 FROM ingredient WHERE name='小白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='小白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='小白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.7 FROM ingredient WHERE name='小白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.3 FROM ingredient WHERE name='小白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 23 FROM ingredient WHERE name='小白菜';
-- 小葱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 27 FROM ingredient WHERE name='小葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.6 FROM ingredient WHERE name='小葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='小葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.2 FROM ingredient WHERE name='小葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.3 FROM ingredient WHERE name='小葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='小葱';
-- 山药
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 56 FROM ingredient WHERE name='山药';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.9 FROM ingredient WHERE name='山药';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='山药';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 12.4 FROM ingredient WHERE name='山药';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.8 FROM ingredient WHERE name='山药';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 51 FROM ingredient WHERE name='山药';
-- 平菇
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 20 FROM ingredient WHERE name='平菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.9 FROM ingredient WHERE name='平菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='平菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.6 FROM ingredient WHERE name='平菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='平菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='平菇';
-- 彩椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='彩椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='彩椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='彩椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.0 FROM ingredient WHERE name='彩椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.0 FROM ingredient WHERE name='彩椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='彩椒';
-- 扁豆
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 38 FROM ingredient WHERE name='扁豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.7 FROM ingredient WHERE name='扁豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='扁豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 8.2 FROM ingredient WHERE name='扁豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='扁豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='扁豆';
-- 杏鲍菇  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 31 FROM ingredient WHERE name='杏鲍菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.3 FROM ingredient WHERE name='杏鲍菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='杏鲍菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 8.3 FROM ingredient WHERE name='杏鲍菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='杏鲍菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='杏鲍菇';
-- 油麦菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 12 FROM ingredient WHERE name='油麦菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='油麦菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='油麦菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.8 FROM ingredient WHERE name='油麦菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.8 FROM ingredient WHERE name='油麦菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='油麦菜';
-- 洋葱
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 40 FROM ingredient WHERE name='洋葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='洋葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='洋葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 9.0 FROM ingredient WHERE name='洋葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.4 FROM ingredient WHERE name='洋葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='洋葱';
-- 海带(鲜)
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 17 FROM ingredient WHERE name='海带(鲜)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='海带(鲜)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='海带(鲜)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.1 FROM ingredient WHERE name='海带(鲜)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='海带(鲜)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 17 FROM ingredient WHERE name='海带(鲜)';
-- 玉米
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 112 FROM ingredient WHERE name='玉米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.0 FROM ingredient WHERE name='玉米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.2 FROM ingredient WHERE name='玉米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 22.8 FROM ingredient WHERE name='玉米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.2 FROM ingredient WHERE name='玉米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 55 FROM ingredient WHERE name='玉米';
-- 生姜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 41 FROM ingredient WHERE name='生姜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.3 FROM ingredient WHERE name='生姜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='生姜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.6 FROM ingredient WHERE name='生姜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='生姜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='生姜';
-- 生菜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 13 FROM ingredient WHERE name='生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.3 FROM ingredient WHERE name='生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.1 FROM ingredient WHERE name='生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='生菜';
-- 番茄
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 19 FROM ingredient WHERE name='番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.0 FROM ingredient WHERE name='番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='番茄';
-- 白萝卜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 21 FROM ingredient WHERE name='白萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='白萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='白萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.0 FROM ingredient WHERE name='白萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='白萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 26 FROM ingredient WHERE name='白萝卜';
-- 空心菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 20 FROM ingredient WHERE name='空心菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.2 FROM ingredient WHERE name='空心菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='空心菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.6 FROM ingredient WHERE name='空心菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='空心菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='空心菜';
-- 红薯
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 86 FROM ingredient WHERE name='红薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.6 FROM ingredient WHERE name='红薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='红薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 20.1 FROM ingredient WHERE name='红薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.2 FROM ingredient WHERE name='红薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 77 FROM ingredient WHERE name='红薯';
-- 红辣椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 32 FROM ingredient WHERE name='红辣椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.3 FROM ingredient WHERE name='红辣椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='红辣椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.4 FROM ingredient WHERE name='红辣椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.2 FROM ingredient WHERE name='红辣椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='红辣椒';
-- 胡萝卜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 41 FROM ingredient WHERE name='胡萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='胡萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='胡萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 8.8 FROM ingredient WHERE name='胡萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.7 FROM ingredient WHERE name='胡萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 71 FROM ingredient WHERE name='胡萝卜';
-- 芋头
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 79 FROM ingredient WHERE name='芋头';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.2 FROM ingredient WHERE name='芋头';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='芋头';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 18.1 FROM ingredient WHERE name='芋头';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='芋头';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 48 FROM ingredient WHERE name='芋头';
-- 芹菜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 14 FROM ingredient WHERE name='芹菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='芹菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='芹菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.3 FROM ingredient WHERE name='芹菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='芹菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='芹菜';
-- 苋菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 25 FROM ingredient WHERE name='苋菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.8 FROM ingredient WHERE name='苋菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='苋菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='苋菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='苋菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='苋菜';
-- 苦瓜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='苦瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='苦瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='苦瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.9 FROM ingredient WHERE name='苦瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='苦瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 24 FROM ingredient WHERE name='苦瓜';
-- 茄子
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 21 FROM ingredient WHERE name='茄子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='茄子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='茄子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.9 FROM ingredient WHERE name='茄子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='茄子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='茄子';
-- 茼蒿
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 14 FROM ingredient WHERE name='茼蒿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.9 FROM ingredient WHERE name='茼蒿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='茼蒿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.7 FROM ingredient WHERE name='茼蒿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='茼蒿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='茼蒿';
-- 莲藕
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 73 FROM ingredient WHERE name='莲藕';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.7 FROM ingredient WHERE name='莲藕';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='莲藕';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 16.4 FROM ingredient WHERE name='莲藕';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.2 FROM ingredient WHERE name='莲藕';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 38 FROM ingredient WHERE name='莲藕';
-- 莴笋
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 14 FROM ingredient WHERE name='莴笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='莴笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='莴笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.8 FROM ingredient WHERE name='莴笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='莴笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='莴笋';
-- 莴笋叶  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 20 FROM ingredient WHERE name='莴笋叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.0 FROM ingredient WHERE name='莴笋叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='莴笋叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.5 FROM ingredient WHERE name='莴笋叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='莴笋叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='莴笋叶';
-- 菜花
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 24 FROM ingredient WHERE name='菜花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.1 FROM ingredient WHERE name='菜花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='菜花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.6 FROM ingredient WHERE name='菜花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='菜花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='菜花';
-- 菠菜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 24 FROM ingredient WHERE name='菠菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.6 FROM ingredient WHERE name='菠菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='菠菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.5 FROM ingredient WHERE name='菠菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.8 FROM ingredient WHERE name='菠菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='菠菜';
-- 西芹  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 16 FROM ingredient WHERE name='西芹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='西芹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='西芹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.5 FROM ingredient WHERE name='西芹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='西芹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='西芹';
-- 西葫芦  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 19 FROM ingredient WHERE name='西葫芦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='西葫芦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='西葫芦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.8 FROM ingredient WHERE name='西葫芦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='西葫芦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='西葫芦';
-- 西蓝花
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 33 FROM ingredient WHERE name='西蓝花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.1 FROM ingredient WHERE name='西蓝花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='西蓝花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.3 FROM ingredient WHERE name='西蓝花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.7 FROM ingredient WHERE name='西蓝花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='西蓝花';
-- 豆角  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 30 FROM ingredient WHERE name='豆角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.7 FROM ingredient WHERE name='豆角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='豆角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.7 FROM ingredient WHERE name='豆角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='豆角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='豆角';
-- 豌豆
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 105 FROM ingredient WHERE name='豌豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.4 FROM ingredient WHERE name='豌豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='豌豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 21.2 FROM ingredient WHERE name='豌豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.0 FROM ingredient WHERE name='豌豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 42 FROM ingredient WHERE name='豌豆';
-- 豌豆苗  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 27 FROM ingredient WHERE name='豌豆苗';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.0 FROM ingredient WHERE name='豌豆苗';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.8 FROM ingredient WHERE name='豌豆苗';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.9 FROM ingredient WHERE name='豌豆苗';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='豌豆苗';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='豌豆苗';
-- 金针菇
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 32 FROM ingredient WHERE name='金针菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.4 FROM ingredient WHERE name='金针菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='金针菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.0 FROM ingredient WHERE name='金针菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='金针菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='金针菇';
-- 银耳(水发)  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 30 FROM ingredient WHERE name='银耳(水发)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='银耳(水发)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='银耳(水发)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.7 FROM ingredient WHERE name='银耳(水发)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='银耳(水发)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='银耳(水发)';
-- 青椒
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='青椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='青椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='青椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='青椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.2 FROM ingredient WHERE name='青椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='青椒';
-- 青萝卜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 23 FROM ingredient WHERE name='青萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='青萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='青萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.2 FROM ingredient WHERE name='青萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='青萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 26 FROM ingredient WHERE name='青萝卜';
-- 韭菜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='韭菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.4 FROM ingredient WHERE name='韭菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='韭菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.6 FROM ingredient WHERE name='韭菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='韭菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='韭菜';
-- 鲜香菇
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='鲜香菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.2 FROM ingredient WHERE name='鲜香菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='鲜香菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.2 FROM ingredient WHERE name='鲜香菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='鲜香菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='鲜香菇';
-- 黄瓜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 16 FROM ingredient WHERE name='黄瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.8 FROM ingredient WHERE name='黄瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='黄瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.9 FROM ingredient WHERE name='黄瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.0 FROM ingredient WHERE name='黄瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 23 FROM ingredient WHERE name='黄瓜';
-- 黑木耳(水发)
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 21 FROM ingredient WHERE name='黑木耳(水发)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='黑木耳(水发)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='黑木耳(水发)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.0 FROM ingredient WHERE name='黑木耳(水发)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.1 FROM ingredient WHERE name='黑木耳(水发)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='黑木耳(水发)';
-- 五花肉
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 508 FROM ingredient WHERE name='五花肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.7 FROM ingredient WHERE name='五花肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 35.3 FROM ingredient WHERE name='五花肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='五花肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='五花肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='五花肉';
-- 牛肉(瘦)
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 106 FROM ingredient WHERE name='牛肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20.2 FROM ingredient WHERE name='牛肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.3 FROM ingredient WHERE name='牛肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.2 FROM ingredient WHERE name='牛肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='牛肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='牛肉(瘦)';
-- 牛腩
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 235 FROM ingredient WHERE name='牛腩';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.1 FROM ingredient WHERE name='牛腩';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 18.4 FROM ingredient WHERE name='牛腩';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='牛腩';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='牛腩';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='牛腩';
-- 猪大肠  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 196 FROM ingredient WHERE name='猪大肠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 6.9 FROM ingredient WHERE name='猪大肠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 18.7 FROM ingredient WHERE name='猪大肠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='猪大肠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪大肠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪大肠';
-- 猪心  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 142 FROM ingredient WHERE name='猪心';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 15.5 FROM ingredient WHERE name='猪心';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 6.7 FROM ingredient WHERE name='猪心';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.8 FROM ingredient WHERE name='猪心';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪心';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪心';
-- 猪排骨
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 278 FROM ingredient WHERE name='猪排骨';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.3 FROM ingredient WHERE name='猪排骨';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 22.6 FROM ingredient WHERE name='猪排骨';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.7 FROM ingredient WHERE name='猪排骨';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪排骨';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪排骨';
-- 猪肉(瘦)
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 143 FROM ingredient WHERE name='猪肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20.3 FROM ingredient WHERE name='猪肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 6.2 FROM ingredient WHERE name='猪肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.5 FROM ingredient WHERE name='猪肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪肉(瘦)';
-- 猪肉(肥瘦)
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 395 FROM ingredient WHERE name='猪肉(肥瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 13.2 FROM ingredient WHERE name='猪肉(肥瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 37.0 FROM ingredient WHERE name='猪肉(肥瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.4 FROM ingredient WHERE name='猪肉(肥瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪肉(肥瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪肉(肥瘦)';
-- 猪肝
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 129 FROM ingredient WHERE name='猪肝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 19.3 FROM ingredient WHERE name='猪肝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.5 FROM ingredient WHERE name='猪肝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.0 FROM ingredient WHERE name='猪肝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪肝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪肝';
-- 猪蹄
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 260 FROM ingredient WHERE name='猪蹄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 22.6 FROM ingredient WHERE name='猪蹄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 18.8 FROM ingredient WHERE name='猪蹄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.6 FROM ingredient WHERE name='猪蹄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪蹄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪蹄';
-- 羊排  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 234 FROM ingredient WHERE name='羊排';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 16.5 FROM ingredient WHERE name='羊排';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 18.7 FROM ingredient WHERE name='羊排';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.5 FROM ingredient WHERE name='羊排';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='羊排';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='羊排';
-- 羊肉(瘦)
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 118 FROM ingredient WHERE name='羊肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20.5 FROM ingredient WHERE name='羊肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.9 FROM ingredient WHERE name='羊肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.2 FROM ingredient WHERE name='羊肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='羊肉(瘦)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='羊肉(瘦)';
-- 驴肉  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 116 FROM ingredient WHERE name='驴肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20.0 FROM ingredient WHERE name='驴肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.2 FROM ingredient WHERE name='驴肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.4 FROM ingredient WHERE name='驴肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='驴肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='驴肉';
-- 鸡爪  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 254 FROM ingredient WHERE name='鸡爪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 23.9 FROM ingredient WHERE name='鸡爪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 16.4 FROM ingredient WHERE name='鸡爪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.7 FROM ingredient WHERE name='鸡爪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.2 FROM ingredient WHERE name='鸡爪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸡爪';
-- 鸡翅
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 194 FROM ingredient WHERE name='鸡翅';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.4 FROM ingredient WHERE name='鸡翅';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 11.8 FROM ingredient WHERE name='鸡翅';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.6 FROM ingredient WHERE name='鸡翅';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='鸡翅';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸡翅';
-- 鸡肉
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 167 FROM ingredient WHERE name='鸡肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 19.3 FROM ingredient WHERE name='鸡肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 9.4 FROM ingredient WHERE name='鸡肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.3 FROM ingredient WHERE name='鸡肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鸡肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸡肉';
-- 鸡胸肉
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 133 FROM ingredient WHERE name='鸡胸肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 19.4 FROM ingredient WHERE name='鸡胸肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 5.0 FROM ingredient WHERE name='鸡胸肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.5 FROM ingredient WHERE name='鸡胸肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鸡胸肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸡胸肉';
-- 鸡腿  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 181 FROM ingredient WHERE name='鸡腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 16.0 FROM ingredient WHERE name='鸡腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 13.0 FROM ingredient WHERE name='鸡腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='鸡腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鸡腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸡腿';
-- 鸭肉
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 240 FROM ingredient WHERE name='鸭肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 15.5 FROM ingredient WHERE name='鸭肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 19.7 FROM ingredient WHERE name='鸭肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.2 FROM ingredient WHERE name='鸭肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鸭肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸭肉';
-- 鸭腿  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 222 FROM ingredient WHERE name='鸭腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 16.0 FROM ingredient WHERE name='鸭腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 17.5 FROM ingredient WHERE name='鸭腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.6 FROM ingredient WHERE name='鸭腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鸭腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸭腿';
-- 鹅肉  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 251 FROM ingredient WHERE name='鹅肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.9 FROM ingredient WHERE name='鹅肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 19.6 FROM ingredient WHERE name='鹅肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='鹅肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鹅肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鹅肉';
-- 三文鱼
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 139 FROM ingredient WHERE name='三文鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.2 FROM ingredient WHERE name='三文鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 7.8 FROM ingredient WHERE name='三文鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='三文鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='三文鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='三文鱼';
-- 基围虾  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 101 FROM ingredient WHERE name='基围虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.2 FROM ingredient WHERE name='基围虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.4 FROM ingredient WHERE name='基围虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.9 FROM ingredient WHERE name='基围虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='基围虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='基围虾';
-- 墨鱼
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 81 FROM ingredient WHERE name='墨鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 15.2 FROM ingredient WHERE name='墨鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.9 FROM ingredient WHERE name='墨鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.4 FROM ingredient WHERE name='墨鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='墨鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='墨鱼';
-- 小龙虾  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 87 FROM ingredient WHERE name='小龙虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.9 FROM ingredient WHERE name='小龙虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.1 FROM ingredient WHERE name='小龙虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='小龙虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='小龙虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='小龙虾';
-- 带鱼
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 127 FROM ingredient WHERE name='带鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.7 FROM ingredient WHERE name='带鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 4.9 FROM ingredient WHERE name='带鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.1 FROM ingredient WHERE name='带鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='带鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='带鱼';
-- 扇贝  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 60 FROM ingredient WHERE name='扇贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 11.1 FROM ingredient WHERE name='扇贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='扇贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.6 FROM ingredient WHERE name='扇贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='扇贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='扇贝';
-- 生蚝  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 73 FROM ingredient WHERE name='生蚝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5.3 FROM ingredient WHERE name='生蚝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.1 FROM ingredient WHERE name='生蚝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 8.2 FROM ingredient WHERE name='生蚝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='生蚝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='生蚝';
-- 章鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 82 FROM ingredient WHERE name='章鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 14.9 FROM ingredient WHERE name='章鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.0 FROM ingredient WHERE name='章鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.1 FROM ingredient WHERE name='章鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='章鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='章鱼';
-- 紫菜(干)
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 207 FROM ingredient WHERE name='紫菜(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 26.7 FROM ingredient WHERE name='紫菜(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.1 FROM ingredient WHERE name='紫菜(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 44.1 FROM ingredient WHERE name='紫菜(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='紫菜(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='紫菜(干)';
-- 草鱼
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 113 FROM ingredient WHERE name='草鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 16.6 FROM ingredient WHERE name='草鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 5.2 FROM ingredient WHERE name='草鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='草鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='草鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='草鱼';
-- 虾仁
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 87 FROM ingredient WHERE name='虾仁';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.6 FROM ingredient WHERE name='虾仁';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.8 FROM ingredient WHERE name='虾仁';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.0 FROM ingredient WHERE name='虾仁';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='虾仁';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='虾仁';
-- 蛏子  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 65 FROM ingredient WHERE name='蛏子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 9.8 FROM ingredient WHERE name='蛏子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='蛏子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='蛏子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='蛏子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='蛏子';
-- 蛤蜊
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 62 FROM ingredient WHERE name='蛤蜊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 10.1 FROM ingredient WHERE name='蛤蜊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.1 FROM ingredient WHERE name='蛤蜊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.8 FROM ingredient WHERE name='蛤蜊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='蛤蜊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='蛤蜊';
-- 螃蟹  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 95 FROM ingredient WHERE name='螃蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.5 FROM ingredient WHERE name='螃蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.6 FROM ingredient WHERE name='螃蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.3 FROM ingredient WHERE name='螃蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='螃蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='螃蟹';
-- 鱿鱼
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 75 FROM ingredient WHERE name='鱿鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.0 FROM ingredient WHERE name='鱿鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.8 FROM ingredient WHERE name='鱿鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='鱿鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鱿鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鱿鱼';
-- 鲈鱼
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 105 FROM ingredient WHERE name='鲈鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.6 FROM ingredient WHERE name='鲈鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.4 FROM ingredient WHERE name='鲈鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='鲈鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鲈鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鲈鱼';
-- 鲤鱼
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 109 FROM ingredient WHERE name='鲤鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.6 FROM ingredient WHERE name='鲤鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 4.1 FROM ingredient WHERE name='鲤鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.5 FROM ingredient WHERE name='鲤鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鲤鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鲤鱼';
-- 鲫鱼
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 108 FROM ingredient WHERE name='鲫鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.1 FROM ingredient WHERE name='鲫鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.7 FROM ingredient WHERE name='鲫鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.8 FROM ingredient WHERE name='鲫鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鲫鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鲫鱼';
-- 鲳鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 142 FROM ingredient WHERE name='鲳鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.5 FROM ingredient WHERE name='鲳鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 7.3 FROM ingredient WHERE name='鲳鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='鲳鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鲳鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鲳鱼';
-- 鳕鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 88 FROM ingredient WHERE name='鳕鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20.4 FROM ingredient WHERE name='鳕鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.5 FROM ingredient WHERE name='鳕鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.5 FROM ingredient WHERE name='鳕鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鳕鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鳕鱼';
-- 黄花鱼
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 99 FROM ingredient WHERE name='黄花鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.6 FROM ingredient WHERE name='黄花鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.5 FROM ingredient WHERE name='黄花鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.8 FROM ingredient WHERE name='黄花鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='黄花鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='黄花鱼';
-- 咸鸭蛋
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 190 FROM ingredient WHERE name='咸鸭蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 12.7 FROM ingredient WHERE name='咸鸭蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 12.7 FROM ingredient WHERE name='咸鸭蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.3 FROM ingredient WHERE name='咸鸭蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='咸鸭蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='咸鸭蛋';
-- 皮蛋
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 171 FROM ingredient WHERE name='皮蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 14.2 FROM ingredient WHERE name='皮蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 10.7 FROM ingredient WHERE name='皮蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.5 FROM ingredient WHERE name='皮蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='皮蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='皮蛋';
-- 鸡蛋
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 144 FROM ingredient WHERE name='鸡蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 13.3 FROM ingredient WHERE name='鸡蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 8.8 FROM ingredient WHERE name='鸡蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.8 FROM ingredient WHERE name='鸡蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.5 FROM ingredient WHERE name='鸡蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='鸡蛋';
-- 鸭蛋
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 180 FROM ingredient WHERE name='鸭蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 12.6 FROM ingredient WHERE name='鸭蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 13.0 FROM ingredient WHERE name='鸭蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.1 FROM ingredient WHERE name='鸭蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='鸭蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='鸭蛋';
-- 鸽蛋  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 168 FROM ingredient WHERE name='鸽蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 10.8 FROM ingredient WHERE name='鸽蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 12.0 FROM ingredient WHERE name='鸽蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.0 FROM ingredient WHERE name='鸽蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='鸽蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='鸽蛋';
-- 鹌鹑蛋
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 160 FROM ingredient WHERE name='鹌鹑蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 12.8 FROM ingredient WHERE name='鹌鹑蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 11.1 FROM ingredient WHERE name='鹌鹑蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.1 FROM ingredient WHERE name='鹌鹑蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='鹌鹑蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='鹌鹑蛋';
-- 内酯豆腐  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 49 FROM ingredient WHERE name='内酯豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5.0 FROM ingredient WHERE name='内酯豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.9 FROM ingredient WHERE name='内酯豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.3 FROM ingredient WHERE name='内酯豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='内酯豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='内酯豆腐';
-- 北豆腐
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 98 FROM ingredient WHERE name='北豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 12.2 FROM ingredient WHERE name='北豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 4.8 FROM ingredient WHERE name='北豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.5 FROM ingredient WHERE name='北豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='北豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='北豆腐';
-- 南豆腐
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 57 FROM ingredient WHERE name='南豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 6.2 FROM ingredient WHERE name='南豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.5 FROM ingredient WHERE name='南豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.4 FROM ingredient WHERE name='南豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='南豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='南豆腐';
-- 毛豆
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 123 FROM ingredient WHERE name='毛豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 13.1 FROM ingredient WHERE name='毛豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 5.0 FROM ingredient WHERE name='毛豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 10.5 FROM ingredient WHERE name='毛豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.2 FROM ingredient WHERE name='毛豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 18 FROM ingredient WHERE name='毛豆';
-- 红豆
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 309 FROM ingredient WHERE name='红豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20.2 FROM ingredient WHERE name='红豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='红豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 63.4 FROM ingredient WHERE name='红豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.0 FROM ingredient WHERE name='红豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 23 FROM ingredient WHERE name='红豆';
-- 绿豆
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 316 FROM ingredient WHERE name='绿豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 21.6 FROM ingredient WHERE name='绿豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.8 FROM ingredient WHERE name='绿豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 62.0 FROM ingredient WHERE name='绿豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.0 FROM ingredient WHERE name='绿豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 27 FROM ingredient WHERE name='绿豆';
-- 腐竹
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 459 FROM ingredient WHERE name='腐竹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 44.6 FROM ingredient WHERE name='腐竹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 21.7 FROM ingredient WHERE name='腐竹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 22.3 FROM ingredient WHERE name='腐竹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='腐竹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='腐竹';
-- 豆浆
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 31 FROM ingredient WHERE name='豆浆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.0 FROM ingredient WHERE name='豆浆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.6 FROM ingredient WHERE name='豆浆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.2 FROM ingredient WHERE name='豆浆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='豆浆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='豆浆';
-- 豆腐(北)
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 98 FROM ingredient WHERE name='豆腐(北)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 12.2 FROM ingredient WHERE name='豆腐(北)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 4.8 FROM ingredient WHERE name='豆腐(北)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.5 FROM ingredient WHERE name='豆腐(北)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='豆腐(北)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='豆腐(北)';
-- 豆腐(嫩)
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 57 FROM ingredient WHERE name='豆腐(嫩)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 6.2 FROM ingredient WHERE name='豆腐(嫩)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.5 FROM ingredient WHERE name='豆腐(嫩)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.4 FROM ingredient WHERE name='豆腐(嫩)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='豆腐(嫩)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='豆腐(嫩)';
-- 豆腐干
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 140 FROM ingredient WHERE name='豆腐干';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 16.2 FROM ingredient WHERE name='豆腐干';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.6 FROM ingredient WHERE name='豆腐干';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 11.5 FROM ingredient WHERE name='豆腐干';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='豆腐干';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='豆腐干';
-- 豆腐皮
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 409 FROM ingredient WHERE name='豆腐皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 44.6 FROM ingredient WHERE name='豆腐皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 17.4 FROM ingredient WHERE name='豆腐皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 18.8 FROM ingredient WHERE name='豆腐皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='豆腐皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='豆腐皮';
-- 黄豆
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 390 FROM ingredient WHERE name='黄豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 35.0 FROM ingredient WHERE name='黄豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 16.0 FROM ingredient WHERE name='黄豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 34.2 FROM ingredient WHERE name='黄豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 5.0 FROM ingredient WHERE name='黄豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 18 FROM ingredient WHERE name='黄豆';
-- 黑豆
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 381 FROM ingredient WHERE name='黑豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 36.0 FROM ingredient WHERE name='黑豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 15.9 FROM ingredient WHERE name='黑豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 33.6 FROM ingredient WHERE name='黑豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.0 FROM ingredient WHERE name='黑豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 18 FROM ingredient WHERE name='黑豆';
-- 奶粉  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 478 FROM ingredient WHERE name='奶粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 24.0 FROM ingredient WHERE name='奶粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 22.0 FROM ingredient WHERE name='奶粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 52.0 FROM ingredient WHERE name='奶粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 36.0 FROM ingredient WHERE name='奶粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 27 FROM ingredient WHERE name='奶粉';
-- 奶酪  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 328 FROM ingredient WHERE name='奶酪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 25.7 FROM ingredient WHERE name='奶酪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 23.5 FROM ingredient WHERE name='奶酪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.5 FROM ingredient WHERE name='奶酪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='奶酪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='奶酪';
-- 淡奶油  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 345 FROM ingredient WHERE name='淡奶油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.2 FROM ingredient WHERE name='淡奶油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 35.0 FROM ingredient WHERE name='淡奶油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.0 FROM ingredient WHERE name='淡奶油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.0 FROM ingredient WHERE name='淡奶油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='淡奶油';
-- 炼乳  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 332 FROM ingredient WHERE name='炼乳';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.5 FROM ingredient WHERE name='炼乳';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 8.0 FROM ingredient WHERE name='炼乳';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 56.0 FROM ingredient WHERE name='炼乳';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 54.0 FROM ingredient WHERE name='炼乳';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 35 FROM ingredient WHERE name='炼乳';
-- 牛奶
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 54 FROM ingredient WHERE name='牛奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.0 FROM ingredient WHERE name='牛奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.2 FROM ingredient WHERE name='牛奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.4 FROM ingredient WHERE name='牛奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='牛奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 27 FROM ingredient WHERE name='牛奶';
-- 羊奶
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 59 FROM ingredient WHERE name='羊奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.8 FROM ingredient WHERE name='羊奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.5 FROM ingredient WHERE name='羊奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='羊奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='羊奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 27 FROM ingredient WHERE name='羊奶';
-- 酸奶
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 72 FROM ingredient WHERE name='酸奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.5 FROM ingredient WHERE name='酸奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.7 FROM ingredient WHERE name='酸奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 9.3 FROM ingredient WHERE name='酸奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 7.0 FROM ingredient WHERE name='酸奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 48 FROM ingredient WHERE name='酸奶';
-- 黄油
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 717 FROM ingredient WHERE name='黄油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='黄油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 81.0 FROM ingredient WHERE name='黄油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.1 FROM ingredient WHERE name='黄油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='黄油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='黄油';
-- 五香粉  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 280 FROM ingredient WHERE name='五香粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.0 FROM ingredient WHERE name='五香粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 8.0 FROM ingredient WHERE name='五香粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 55.0 FROM ingredient WHERE name='五香粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.0 FROM ingredient WHERE name='五香粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='五香粉';
-- 八角  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 195 FROM ingredient WHERE name='八角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.8 FROM ingredient WHERE name='八角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 5.6 FROM ingredient WHERE name='八角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 35.0 FROM ingredient WHERE name='八角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='八角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='八角';
-- 冰糖
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 397 FROM ingredient WHERE name='冰糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.1 FROM ingredient WHERE name='冰糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='冰糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 98.5 FROM ingredient WHERE name='冰糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 98.5 FROM ingredient WHERE name='冰糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='冰糖';
-- 干辣椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 212 FROM ingredient WHERE name='干辣椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 15.0 FROM ingredient WHERE name='干辣椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 12.0 FROM ingredient WHERE name='干辣椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 9.0 FROM ingredient WHERE name='干辣椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 5.0 FROM ingredient WHERE name='干辣椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='干辣椒';
-- 料酒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 80 FROM ingredient WHERE name='料酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.3 FROM ingredient WHERE name='料酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='料酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.0 FROM ingredient WHERE name='料酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='料酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='料酒';
-- 桂皮  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 247 FROM ingredient WHERE name='桂皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.0 FROM ingredient WHERE name='桂皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.2 FROM ingredient WHERE name='桂皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 80.6 FROM ingredient WHERE name='桂皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.2 FROM ingredient WHERE name='桂皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='桂皮';
-- 橄榄油
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 899 FROM ingredient WHERE name='橄榄油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='橄榄油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='橄榄油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='橄榄油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='橄榄油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='橄榄油';
-- 淀粉
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 349 FROM ingredient WHERE name='淀粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.3 FROM ingredient WHERE name='淀粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='淀粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 86.6 FROM ingredient WHERE name='淀粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='淀粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='淀粉';
-- 猪油
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 897 FROM ingredient WHERE name='猪油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='猪油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.6 FROM ingredient WHERE name='猪油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.2 FROM ingredient WHERE name='猪油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪油';
-- 甜面酱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 136 FROM ingredient WHERE name='甜面酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5.5 FROM ingredient WHERE name='甜面酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='甜面酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 28.5 FROM ingredient WHERE name='甜面酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 18.0 FROM ingredient WHERE name='甜面酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='甜面酱';
-- 番茄酱
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 81 FROM ingredient WHERE name='番茄酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.9 FROM ingredient WHERE name='番茄酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='番茄酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 16.9 FROM ingredient WHERE name='番茄酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 14.9 FROM ingredient WHERE name='番茄酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='番茄酱';
-- 白糖
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 400 FROM ingredient WHERE name='白糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='白糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='白糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 99.9 FROM ingredient WHERE name='白糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 99.9 FROM ingredient WHERE name='白糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='白糖';
-- 白胡椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 255 FROM ingredient WHERE name='白胡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 9.4 FROM ingredient WHERE name='白胡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.0 FROM ingredient WHERE name='白胡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 65.0 FROM ingredient WHERE name='白胡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='白胡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='白胡椒';
-- 红糖  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 389 FROM ingredient WHERE name='红糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.7 FROM ingredient WHERE name='红糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='红糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 96.6 FROM ingredient WHERE name='红糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 92.0 FROM ingredient WHERE name='红糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='红糖';
-- 芝麻油
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 898 FROM ingredient WHERE name='芝麻油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='芝麻油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.7 FROM ingredient WHERE name='芝麻油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.2 FROM ingredient WHERE name='芝麻油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='芝麻油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='芝麻油';
-- 花椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 258 FROM ingredient WHERE name='花椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 6.7 FROM ingredient WHERE name='花椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 8.9 FROM ingredient WHERE name='花椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 56.6 FROM ingredient WHERE name='花椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='花椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='花椒';
-- 花生油
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 899 FROM ingredient WHERE name='花生油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='花生油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='花生油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='花生油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='花生油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='花生油';
-- 菜籽油
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 899 FROM ingredient WHERE name='菜籽油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='菜籽油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='菜籽油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='菜籽油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='菜籽油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='菜籽油';
-- 蚝油  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 82 FROM ingredient WHERE name='蚝油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.5 FROM ingredient WHERE name='蚝油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='蚝油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 16.0 FROM ingredient WHERE name='蚝油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 12.0 FROM ingredient WHERE name='蚝油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='蚝油';
-- 豆瓣酱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 178 FROM ingredient WHERE name='豆瓣酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 8.0 FROM ingredient WHERE name='豆瓣酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 6.8 FROM ingredient WHERE name='豆瓣酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 19.0 FROM ingredient WHERE name='豆瓣酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.0 FROM ingredient WHERE name='豆瓣酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='豆瓣酱';
-- 酱油(生抽)
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 63 FROM ingredient WHERE name='酱油(生抽)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5.6 FROM ingredient WHERE name='酱油(生抽)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='酱油(生抽)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.1 FROM ingredient WHERE name='酱油(生抽)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='酱油(生抽)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='酱油(生抽)';
-- 酱油(老抽)  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 65 FROM ingredient WHERE name='酱油(老抽)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 6.0 FROM ingredient WHERE name='酱油(老抽)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='酱油(老抽)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.0 FROM ingredient WHERE name='酱油(老抽)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='酱油(老抽)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='酱油(老抽)';
-- 醋
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 31 FROM ingredient WHERE name='醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.1 FROM ingredient WHERE name='醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.9 FROM ingredient WHERE name='醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='醋';
-- 食用油
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 899 FROM ingredient WHERE name='食用油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='食用油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='食用油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='食用油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='食用油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='食用油';
-- 食盐
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 0 FROM ingredient WHERE name='食盐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='食盐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='食盐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='食盐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='食盐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='食盐';
-- 黑胡椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 255 FROM ingredient WHERE name='黑胡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 10.0 FROM ingredient WHERE name='黑胡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.0 FROM ingredient WHERE name='黑胡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 64.0 FROM ingredient WHERE name='黑胡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='黑胡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='黑胡椒';
-- 哈密瓜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 34 FROM ingredient WHERE name='哈密瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.5 FROM ingredient WHERE name='哈密瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='哈密瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.7 FROM ingredient WHERE name='哈密瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 6.0 FROM ingredient WHERE name='哈密瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 65 FROM ingredient WHERE name='哈密瓜';
-- 提子  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 50 FROM ingredient WHERE name='提子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.5 FROM ingredient WHERE name='提子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='提子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 12.0 FROM ingredient WHERE name='提子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 10.0 FROM ingredient WHERE name='提子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 46 FROM ingredient WHERE name='提子';
-- 柚子
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 42 FROM ingredient WHERE name='柚子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.8 FROM ingredient WHERE name='柚子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='柚子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 9.5 FROM ingredient WHERE name='柚子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.8 FROM ingredient WHERE name='柚子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 25 FROM ingredient WHERE name='柚子';
-- 桃
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 51 FROM ingredient WHERE name='桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 12.2 FROM ingredient WHERE name='桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 8.0 FROM ingredient WHERE name='桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='桃';
-- 梨
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 51 FROM ingredient WHERE name='梨';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.3 FROM ingredient WHERE name='梨';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='梨';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 13.3 FROM ingredient WHERE name='梨';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 7.0 FROM ingredient WHERE name='梨';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 36 FROM ingredient WHERE name='梨';
-- 榴莲
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 147 FROM ingredient WHERE name='榴莲';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='榴莲';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.3 FROM ingredient WHERE name='榴莲';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 27.0 FROM ingredient WHERE name='榴莲';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 14.0 FROM ingredient WHERE name='榴莲';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 49 FROM ingredient WHERE name='榴莲';
-- 樱桃
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 46 FROM ingredient WHERE name='樱桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='樱桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='樱桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 10.2 FROM ingredient WHERE name='樱桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 8.0 FROM ingredient WHERE name='樱桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 22 FROM ingredient WHERE name='樱桃';
-- 橘子
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 47 FROM ingredient WHERE name='橘子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='橘子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='橘子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 11.6 FROM ingredient WHERE name='橘子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 8.8 FROM ingredient WHERE name='橘子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 43 FROM ingredient WHERE name='橘子';
-- 橙子
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 48 FROM ingredient WHERE name='橙子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.8 FROM ingredient WHERE name='橙子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='橙子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 11.1 FROM ingredient WHERE name='橙子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 9.5 FROM ingredient WHERE name='橙子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 43 FROM ingredient WHERE name='橙子';
-- 火龙果  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 51 FROM ingredient WHERE name='火龙果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='火龙果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='火龙果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 13.3 FROM ingredient WHERE name='火龙果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 8.0 FROM ingredient WHERE name='火龙果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 25 FROM ingredient WHERE name='火龙果';
-- 猕猴桃
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 61 FROM ingredient WHERE name='猕猴桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.8 FROM ingredient WHERE name='猕猴桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='猕猴桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 14.5 FROM ingredient WHERE name='猕猴桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 9.2 FROM ingredient WHERE name='猕猴桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 50 FROM ingredient WHERE name='猕猴桃';
-- 石榴  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 72 FROM ingredient WHERE name='石榴';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='石榴';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='石榴';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 18.7 FROM ingredient WHERE name='石榴';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 13.0 FROM ingredient WHERE name='石榴';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 35 FROM ingredient WHERE name='石榴';
-- 芒果  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 35 FROM ingredient WHERE name='芒果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.6 FROM ingredient WHERE name='芒果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='芒果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 8.3 FROM ingredient WHERE name='芒果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 7.0 FROM ingredient WHERE name='芒果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 55 FROM ingredient WHERE name='芒果';
-- 苹果
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 54 FROM ingredient WHERE name='苹果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.2 FROM ingredient WHERE name='苹果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='苹果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 13.5 FROM ingredient WHERE name='苹果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 9.3 FROM ingredient WHERE name='苹果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 36 FROM ingredient WHERE name='苹果';
-- 草莓
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 32 FROM ingredient WHERE name='草莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='草莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='草莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.1 FROM ingredient WHERE name='草莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.7 FROM ingredient WHERE name='草莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 29 FROM ingredient WHERE name='草莓';
-- 荔枝
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 70 FROM ingredient WHERE name='荔枝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='荔枝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='荔枝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 16.6 FROM ingredient WHERE name='荔枝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 14.0 FROM ingredient WHERE name='荔枝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 50 FROM ingredient WHERE name='荔枝';
-- 菠萝
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 44 FROM ingredient WHERE name='菠萝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.5 FROM ingredient WHERE name='菠萝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='菠萝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 10.8 FROM ingredient WHERE name='菠萝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 9.9 FROM ingredient WHERE name='菠萝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 66 FROM ingredient WHERE name='菠萝';
-- 葡萄
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 44 FROM ingredient WHERE name='葡萄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.5 FROM ingredient WHERE name='葡萄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='葡萄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 10.3 FROM ingredient WHERE name='葡萄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 8.8 FROM ingredient WHERE name='葡萄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 43 FROM ingredient WHERE name='葡萄';
-- 蓝莓  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 57 FROM ingredient WHERE name='蓝莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.7 FROM ingredient WHERE name='蓝莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='蓝莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 14.5 FROM ingredient WHERE name='蓝莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 10.0 FROM ingredient WHERE name='蓝莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 53 FROM ingredient WHERE name='蓝莓';
-- 西瓜
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='西瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.6 FROM ingredient WHERE name='西瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='西瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.8 FROM ingredient WHERE name='西瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 5.2 FROM ingredient WHERE name='西瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 72 FROM ingredient WHERE name='西瓜';
-- 香蕉
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 93 FROM ingredient WHERE name='香蕉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='香蕉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='香蕉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 22.0 FROM ingredient WHERE name='香蕉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 12.2 FROM ingredient WHERE name='香蕉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 52 FROM ingredient WHERE name='香蕉';
-- 龙眼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 71 FROM ingredient WHERE name='龙眼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='龙眼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='龙眼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 16.6 FROM ingredient WHERE name='龙眼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 14.0 FROM ingredient WHERE name='龙眼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 50 FROM ingredient WHERE name='龙眼';
-- 大米
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 346 FROM ingredient WHERE name='大米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.4 FROM ingredient WHERE name='大米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.8 FROM ingredient WHERE name='大米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 77.9 FROM ingredient WHERE name='大米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='大米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='大米';
-- 小米
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 358 FROM ingredient WHERE name='小米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 9.0 FROM ingredient WHERE name='小米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.1 FROM ingredient WHERE name='小米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 75.1 FROM ingredient WHERE name='小米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='小米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 71 FROM ingredient WHERE name='小米';
-- 挂面
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 344 FROM ingredient WHERE name='挂面';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 9.6 FROM ingredient WHERE name='挂面';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.7 FROM ingredient WHERE name='挂面';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 75.1 FROM ingredient WHERE name='挂面';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='挂面';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 82 FROM ingredient WHERE name='挂面';
-- 燕麦
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 367 FROM ingredient WHERE name='燕麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 15.0 FROM ingredient WHERE name='燕麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 6.7 FROM ingredient WHERE name='燕麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 66.9 FROM ingredient WHERE name='燕麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='燕麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 55 FROM ingredient WHERE name='燕麦';
-- 玉米面
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 341 FROM ingredient WHERE name='玉米面';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 8.1 FROM ingredient WHERE name='玉米面';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.3 FROM ingredient WHERE name='玉米面';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 75.2 FROM ingredient WHERE name='玉米面';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='玉米面';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 68 FROM ingredient WHERE name='玉米面';
-- 米饭
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 116 FROM ingredient WHERE name='米饭';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.6 FROM ingredient WHERE name='米饭';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='米饭';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 25.9 FROM ingredient WHERE name='米饭';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='米饭';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 83 FROM ingredient WHERE name='米饭';
-- 糯米
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 350 FROM ingredient WHERE name='糯米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.3 FROM ingredient WHERE name='糯米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.0 FROM ingredient WHERE name='糯米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 78.3 FROM ingredient WHERE name='糯米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='糯米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='糯米';
-- 荞麦
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 337 FROM ingredient WHERE name='荞麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 9.3 FROM ingredient WHERE name='荞麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.3 FROM ingredient WHERE name='荞麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 73.0 FROM ingredient WHERE name='荞麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='荞麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 54 FROM ingredient WHERE name='荞麦';
-- 面粉
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 350 FROM ingredient WHERE name='面粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 11.2 FROM ingredient WHERE name='面粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.5 FROM ingredient WHERE name='面粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 73.6 FROM ingredient WHERE name='面粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='面粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='面粉';
-- 馒头
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 223 FROM ingredient WHERE name='馒头';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.0 FROM ingredient WHERE name='馒头';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.1 FROM ingredient WHERE name='馒头';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 47.0 FROM ingredient WHERE name='馒头';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='馒头';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 88 FROM ingredient WHERE name='馒头';
-- 鲜面条  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 280 FROM ingredient WHERE name='鲜面条';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 8.0 FROM ingredient WHERE name='鲜面条';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.5 FROM ingredient WHERE name='鲜面条';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 60.0 FROM ingredient WHERE name='鲜面条';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鲜面条';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 82 FROM ingredient WHERE name='鲜面条';

COMMIT;

-- 验证：SELECT COUNT(*) FROM ingredient; (目标 150-200)
-- 抽查 UTF-8：SELECT name,HEX(name) FROM ingredient WHERE name='牛肉(瘦)';  -- 应为 E7 89 9B ... 非 0001