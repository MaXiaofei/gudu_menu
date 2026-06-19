-- ============================================================
-- V23__ingredients_ext.sql
-- 烟火小食单：扩充常见中国食材库 ~150 种 + 营养 EAV(6 项/食材)
-- 营养数据参考《中国食物成分表》(per 100g)；标注【估】为同品类典型参考值
-- metric_id: 1=calorie(kcal) 2=protein(g) 3=fat(g) 4=carb(g) 5=sugar(g) 6=gi
-- 幂等：by name 删再插（含已存在的 39 种 demo 数据，营养值同源覆盖）
-- price=0（已移除价格展示）；UTF-8
-- ============================================================

START TRANSACTION;

-- 1) 幂等：先删营养再删本体（按 name）
DELETE FROM ingredient_nutrition WHERE ingredient_id IN (
  SELECT id FROM ingredient WHERE name IN (
    '丝瓜','冬瓜','包菜','南瓜','圆白菜','土豆','大白菜','大葱','大蒜','娃娃菜','小白菜','小葱',
    '山药','平菇','彩椒','扁豆','杏鲍菇','油麦菜','洋葱','海带(鲜)','玉米','生姜','生菜','番茄',
    '白萝卜','空心菜','红薯','红辣椒','胡萝卜','芋头','芹菜','苋菜','苦瓜','茄子','茼蒿','莲藕',
    '莴笋','莴笋叶','菜花','菠菜','西芹','西葫芦','西蓝花','豆角','豌豆','豌豆苗','金针菇','银耳(水发)',
    '青椒','青萝卜','韭菜','鲜香菇','黄瓜','黑木耳(水发)','五花肉','牛肉(瘦)','牛腩','猪大肠','猪心','猪排骨',
    '猪肉(瘦)','猪肉(肥瘦)','猪肝','猪蹄','羊排','羊肉(瘦)','驴肉','鸡爪','鸡翅','鸡肉','鸡胸肉','鸡腿',
    '鸭肉','鸭腿','鹅肉','三文鱼','基围虾','墨鱼','小龙虾','带鱼','扇贝','生蚝','章鱼','紫菜(干)',
    '草鱼','虾仁','蛏子','蛤蜊','螃蟹','鱿鱼','鲈鱼','鲤鱼','鲫鱼','鲳鱼','鳕鱼','黄花鱼',
    '咸鸭蛋','皮蛋','鸡蛋','鸭蛋','鸽蛋','鹌鹑蛋','内酯豆腐','北豆腐','南豆腐','毛豆','红豆','绿豆',
    '腐竹','豆浆','豆腐(北)','豆腐(嫩)','豆腐干','豆腐皮','黄豆','黑豆','奶粉','奶酪','淡奶油','炼乳',
    '牛奶','羊奶','酸奶','黄油','五香粉','八角','冰糖','干辣椒','料酒','桂皮','橄榄油','淀粉',
    '猪油','甜面酱','番茄酱','白糖','白胡椒','红糖','芝麻油','花椒','花生油','菜籽油','蚝油','豆瓣酱',
    '酱油(生抽)','酱油(老抽)','醋','食用油','食盐','黑胡椒','哈密瓜','提子','柚子','桃','梨','榴莲',
    '樱桃','橘子','橙子','火龙果','猕猴桃','石榴','芒果','苹果','草莓','荔枝','菠萝','葡萄',
    '蓝莓','西瓜','香蕉','龙眼','大米','小米','挂面','燕麦','玉米面','米饭','糯米','荞麦',
    '面粉','馒头','鲜面条'
  )
);
DELETE FROM ingredient WHERE name IN (
    '丝瓜','冬瓜','包菜','南瓜','圆白菜','土豆','大白菜','大葱','大蒜','娃娃菜','小白菜','小葱',
    '山药','平菇','彩椒','扁豆','杏鲍菇','油麦菜','洋葱','海带(鲜)','玉米','生姜','生菜','番茄',
    '白萝卜','空心菜','红薯','红辣椒','胡萝卜','芋头','芹菜','苋菜','苦瓜','茄子','茼蒿','莲藕',
    '莴笋','莴笋叶','菜花','菠菜','西芹','西葫芦','西蓝花','豆角','豌豆','豌豆苗','金针菇','银耳(水发)',
    '青椒','青萝卜','韭菜','鲜香菇','黄瓜','黑木耳(水发)','五花肉','牛肉(瘦)','牛腩','猪大肠','猪心','猪排骨',
    '猪肉(瘦)','猪肉(肥瘦)','猪肝','猪蹄','羊排','羊肉(瘦)','驴肉','鸡爪','鸡翅','鸡肉','鸡胸肉','鸡腿',
    '鸭肉','鸭腿','鹅肉','三文鱼','基围虾','墨鱼','小龙虾','带鱼','扇贝','生蚝','章鱼','紫菜(干)',
    '草鱼','虾仁','蛏子','蛤蜊','螃蟹','鱿鱼','鲈鱼','鲤鱼','鲫鱼','鲳鱼','鳕鱼','黄花鱼',
    '咸鸭蛋','皮蛋','鸡蛋','鸭蛋','鸽蛋','鹌鹑蛋','内酯豆腐','北豆腐','南豆腐','毛豆','红豆','绿豆',
    '腐竹','豆浆','豆腐(北)','豆腐(嫩)','豆腐干','豆腐皮','黄豆','黑豆','奶粉','奶酪','淡奶油','炼乳',
    '牛奶','羊奶','酸奶','黄油','五香粉','八角','冰糖','干辣椒','料酒','桂皮','橄榄油','淀粉',
    '猪油','甜面酱','番茄酱','白糖','白胡椒','红糖','芝麻油','花椒','花生油','菜籽油','蚝油','豆瓣酱',
    '酱油(生抽)','酱油(老抽)','醋','食用油','食盐','黑胡椒','哈密瓜','提子','柚子','桃','梨','榴莲',
    '樱桃','橘子','橙子','火龙果','猕猴桃','石榴','芒果','苹果','草莓','荔枝','菠萝','葡萄',
    '蓝莓','西瓜','香蕉','龙眼','大米','小米','挂面','燕麦','玉米面','米饭','糯米','荞麦',
    '面粉','馒头','鲜面条'
);

-- 2) 批量插入食材
INSERT INTO ingredient(name, unit_id, price, purchase_category_id) VALUES
  ('丝瓜', 20, 0, 24),
  ('冬瓜', 20, 0, 24),
  ('包菜', 20, 0, 24),
  ('南瓜', 20, 0, 24),
  ('圆白菜', 20, 0, 24),
  ('土豆', 20, 0, 24),
  ('大白菜', 20, 0, 24),
  ('大葱', 20, 0, 24),
  ('大蒜', 20, 0, 24),
  ('娃娃菜', 20, 0, 24),
  ('小白菜', 20, 0, 24),
  ('小葱', 20, 0, 24),
  ('山药', 20, 0, 24),
  ('平菇', 20, 0, 24),
  ('彩椒', 20, 0, 24),
  ('扁豆', 20, 0, 24),
  ('杏鲍菇', 20, 0, 24),
  ('油麦菜', 20, 0, 24),
  ('洋葱', 20, 0, 24),
  ('海带(鲜)', 20, 0, 24),
  ('玉米', 20, 0, 24),
  ('生姜', 20, 0, 24),
  ('生菜', 20, 0, 24),
  ('番茄', 20, 0, 24),
  ('白萝卜', 20, 0, 24),
  ('空心菜', 20, 0, 24),
  ('红薯', 20, 0, 24),
  ('红辣椒', 20, 0, 24),
  ('胡萝卜', 20, 0, 24),
  ('芋头', 20, 0, 24),
  ('芹菜', 20, 0, 24),
  ('苋菜', 20, 0, 24),
  ('苦瓜', 20, 0, 24),
  ('茄子', 20, 0, 24),
  ('茼蒿', 20, 0, 24),
  ('莲藕', 20, 0, 24),
  ('莴笋', 20, 0, 24),
  ('莴笋叶', 20, 0, 24),
  ('菜花', 20, 0, 24),
  ('菠菜', 20, 0, 24),
  ('西芹', 20, 0, 24),
  ('西葫芦', 20, 0, 24),
  ('西蓝花', 20, 0, 24),
  ('豆角', 20, 0, 24),
  ('豌豆', 20, 0, 24),
  ('豌豆苗', 20, 0, 24),
  ('金针菇', 20, 0, 24),
  ('银耳(水发)', 20, 0, 24),
  ('青椒', 20, 0, 24),
  ('青萝卜', 20, 0, 24),
  ('韭菜', 20, 0, 24),
  ('鲜香菇', 20, 0, 24),
  ('黄瓜', 20, 0, 24),
  ('黑木耳(水发)', 20, 0, 24),
  ('五花肉', 20, 0, 25),
  ('牛肉(瘦)', 20, 0, 25),
  ('牛腩', 20, 0, 25),
  ('猪大肠', 20, 0, 25),
  ('猪心', 20, 0, 25),
  ('猪排骨', 20, 0, 25),
  ('猪肉(瘦)', 20, 0, 25),
  ('猪肉(肥瘦)', 20, 0, 25),
  ('猪肝', 20, 0, 25),
  ('猪蹄', 20, 0, 25),
  ('羊排', 20, 0, 25),
  ('羊肉(瘦)', 20, 0, 25),
  ('驴肉', 20, 0, 25),
  ('鸡爪', 20, 0, 25),
  ('鸡翅', 20, 0, 25),
  ('鸡肉', 20, 0, 25),
  ('鸡胸肉', 20, 0, 25),
  ('鸡腿', 20, 0, 25),
  ('鸭肉', 20, 0, 25),
  ('鸭腿', 20, 0, 25),
  ('鹅肉', 20, 0, 25),
  ('三文鱼', 20, 0, 26),
  ('基围虾', 20, 0, 26),
  ('墨鱼', 20, 0, 26),
  ('小龙虾', 20, 0, 26),
  ('带鱼', 20, 0, 26),
  ('扇贝', 20, 0, 26),
  ('生蚝', 20, 0, 26),
  ('章鱼', 20, 0, 26),
  ('紫菜(干)', 20, 0, 26),
  ('草鱼', 20, 0, 26),
  ('虾仁', 20, 0, 26),
  ('蛏子', 20, 0, 26),
  ('蛤蜊', 20, 0, 26),
  ('螃蟹', 20, 0, 26),
  ('鱿鱼', 20, 0, 26),
  ('鲈鱼', 20, 0, 26),
  ('鲤鱼', 20, 0, 26),
  ('鲫鱼', 20, 0, 26),
  ('鲳鱼', 20, 0, 26),
  ('鳕鱼', 20, 0, 26),
  ('黄花鱼', 20, 0, 26),
  ('咸鸭蛋', 22, 0, 27),
  ('皮蛋', 22, 0, 27),
  ('鸡蛋', 22, 0, 27),
  ('鸭蛋', 22, 0, 27),
  ('鸽蛋', 22, 0, 27),
  ('鹌鹑蛋', 22, 0, 27),
  ('内酯豆腐', 20, 0, 28),
  ('北豆腐', 20, 0, 28),
  ('南豆腐', 20, 0, 28),
  ('毛豆', 20, 0, 28),
  ('红豆', 20, 0, 28),
  ('绿豆', 20, 0, 28),
  ('腐竹', 20, 0, 28),
  ('豆浆', 21, 0, 28),
  ('豆腐(北)', 20, 0, 28),
  ('豆腐(嫩)', 20, 0, 28),
  ('豆腐干', 20, 0, 28),
  ('豆腐皮', 20, 0, 28),
  ('黄豆', 20, 0, 28),
  ('黑豆', 20, 0, 28),
  ('奶粉', 20, 0, 29),
  ('奶酪', 20, 0, 29),
  ('淡奶油', 21, 0, 29),
  ('炼乳', 21, 0, 29),
  ('牛奶', 21, 0, 29),
  ('羊奶', 21, 0, 29),
  ('酸奶', 21, 0, 29),
  ('黄油', 20, 0, 29),
  ('五香粉', 20, 0, 30),
  ('八角', 20, 0, 30),
  ('冰糖', 20, 0, 30),
  ('干辣椒', 20, 0, 30),
  ('料酒', 21, 0, 30),
  ('桂皮', 20, 0, 30),
  ('橄榄油', 21, 0, 30),
  ('淀粉', 20, 0, 30),
  ('猪油', 20, 0, 30),
  ('甜面酱', 20, 0, 30),
  ('番茄酱', 20, 0, 30),
  ('白糖', 20, 0, 30),
  ('白胡椒', 20, 0, 30),
  ('红糖', 20, 0, 30),
  ('芝麻油', 21, 0, 30),
  ('花椒', 20, 0, 30),
  ('花生油', 21, 0, 30),
  ('菜籽油', 21, 0, 30),
  ('蚝油', 21, 0, 30),
  ('豆瓣酱', 20, 0, 30),
  ('酱油(生抽)', 21, 0, 30),
  ('酱油(老抽)', 21, 0, 30),
  ('醋', 21, 0, 30),
  ('食用油', 21, 0, 30),
  ('食盐', 20, 0, 30),
  ('黑胡椒', 20, 0, 30),
  ('哈密瓜', 22, 0, 31),
  ('提子', 22, 0, 31),
  ('柚子', 22, 0, 31),
  ('桃', 22, 0, 31),
  ('梨', 22, 0, 31),
  ('榴莲', 22, 0, 31),
  ('樱桃', 22, 0, 31),
  ('橘子', 22, 0, 31),
  ('橙子', 22, 0, 31),
  ('火龙果', 22, 0, 31),
  ('猕猴桃', 22, 0, 31),
  ('石榴', 22, 0, 31),
  ('芒果', 22, 0, 31),
  ('苹果', 22, 0, 31),
  ('草莓', 22, 0, 31),
  ('荔枝', 22, 0, 31),
  ('菠萝', 22, 0, 31),
  ('葡萄', 22, 0, 31),
  ('蓝莓', 22, 0, 31),
  ('西瓜', 22, 0, 31),
  ('香蕉', 22, 0, 31),
  ('龙眼', 22, 0, 31),
  ('大米', 20, 0, NULL),
  ('小米', 20, 0, NULL),
  ('挂面', 20, 0, NULL),
  ('燕麦', 20, 0, NULL),
  ('玉米面', 20, 0, NULL),
  ('米饭', 22, 0, NULL),
  ('糯米', 20, 0, NULL),
  ('荞麦', 20, 0, NULL),
  ('面粉', 20, 0, NULL),
  ('馒头', 22, 0, NULL),
  ('鲜面条', 20, 0, NULL);

-- 3) 每食材 6 项营养（INSERT...SELECT by name）
-- 丝瓜  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 20 FROM ingredient WHERE name='丝瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='丝瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='丝瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.2 FROM ingredient WHERE name='丝瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.8 FROM ingredient WHERE name='丝瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 25 FROM ingredient WHERE name='丝瓜';
-- 冬瓜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 11 FROM ingredient WHERE name='冬瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.4 FROM ingredient WHERE name='冬瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='冬瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.6 FROM ingredient WHERE name='冬瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='冬瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 20 FROM ingredient WHERE name='冬瓜';
-- 包菜  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='包菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='包菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='包菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.6 FROM ingredient WHERE name='包菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.0 FROM ingredient WHERE name='包菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 26 FROM ingredient WHERE name='包菜';
-- 南瓜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='南瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.7 FROM ingredient WHERE name='南瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='南瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.3 FROM ingredient WHERE name='南瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.0 FROM ingredient WHERE name='南瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 75 FROM ingredient WHERE name='南瓜';
-- 圆白菜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='圆白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='圆白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='圆白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.6 FROM ingredient WHERE name='圆白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.0 FROM ingredient WHERE name='圆白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 26 FROM ingredient WHERE name='圆白菜';
-- 土豆
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 76 FROM ingredient WHERE name='土豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.0 FROM ingredient WHERE name='土豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='土豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 17.2 FROM ingredient WHERE name='土豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.8 FROM ingredient WHERE name='土豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 78 FROM ingredient WHERE name='土豆';
-- 大白菜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 17 FROM ingredient WHERE name='大白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='大白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='大白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.2 FROM ingredient WHERE name='大白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='大白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 23 FROM ingredient WHERE name='大白菜';
-- 大葱
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 33 FROM ingredient WHERE name='大葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.7 FROM ingredient WHERE name='大葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='大葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.5 FROM ingredient WHERE name='大葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='大葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='大葱';
-- 大蒜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 126 FROM ingredient WHERE name='大蒜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.5 FROM ingredient WHERE name='大蒜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='大蒜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 27.6 FROM ingredient WHERE name='大蒜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='大蒜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='大蒜';
-- 娃娃菜  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 13 FROM ingredient WHERE name='娃娃菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='娃娃菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='娃娃菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.6 FROM ingredient WHERE name='娃娃菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='娃娃菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 23 FROM ingredient WHERE name='娃娃菜';
-- 小白菜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 15 FROM ingredient WHERE name='小白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='小白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='小白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.7 FROM ingredient WHERE name='小白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.3 FROM ingredient WHERE name='小白菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 23 FROM ingredient WHERE name='小白菜';
-- 小葱  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 27 FROM ingredient WHERE name='小葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.6 FROM ingredient WHERE name='小葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='小葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.2 FROM ingredient WHERE name='小葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.3 FROM ingredient WHERE name='小葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='小葱';
-- 山药
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 56 FROM ingredient WHERE name='山药';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.9 FROM ingredient WHERE name='山药';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='山药';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 12.4 FROM ingredient WHERE name='山药';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.8 FROM ingredient WHERE name='山药';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 51 FROM ingredient WHERE name='山药';
-- 平菇
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 20 FROM ingredient WHERE name='平菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.9 FROM ingredient WHERE name='平菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='平菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.6 FROM ingredient WHERE name='平菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='平菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='平菇';
-- 彩椒  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='彩椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='彩椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='彩椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.0 FROM ingredient WHERE name='彩椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.0 FROM ingredient WHERE name='彩椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='彩椒';
-- 扁豆
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 38 FROM ingredient WHERE name='扁豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.7 FROM ingredient WHERE name='扁豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='扁豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 8.2 FROM ingredient WHERE name='扁豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='扁豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='扁豆';
-- 杏鲍菇  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 31 FROM ingredient WHERE name='杏鲍菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.3 FROM ingredient WHERE name='杏鲍菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='杏鲍菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 8.3 FROM ingredient WHERE name='杏鲍菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='杏鲍菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='杏鲍菇';
-- 油麦菜  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 12 FROM ingredient WHERE name='油麦菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='油麦菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='油麦菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.8 FROM ingredient WHERE name='油麦菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.8 FROM ingredient WHERE name='油麦菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='油麦菜';
-- 洋葱
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 40 FROM ingredient WHERE name='洋葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='洋葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='洋葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 9.0 FROM ingredient WHERE name='洋葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.4 FROM ingredient WHERE name='洋葱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='洋葱';
-- 海带(鲜)
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 17 FROM ingredient WHERE name='海带(鲜)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='海带(鲜)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='海带(鲜)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.1 FROM ingredient WHERE name='海带(鲜)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='海带(鲜)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 17 FROM ingredient WHERE name='海带(鲜)';
-- 玉米
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 112 FROM ingredient WHERE name='玉米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.0 FROM ingredient WHERE name='玉米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.2 FROM ingredient WHERE name='玉米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 22.8 FROM ingredient WHERE name='玉米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.2 FROM ingredient WHERE name='玉米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 55 FROM ingredient WHERE name='玉米';
-- 生姜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 41 FROM ingredient WHERE name='生姜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.3 FROM ingredient WHERE name='生姜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='生姜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.6 FROM ingredient WHERE name='生姜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='生姜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='生姜';
-- 生菜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 13 FROM ingredient WHERE name='生菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.3 FROM ingredient WHERE name='生菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='生菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.1 FROM ingredient WHERE name='生菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='生菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='生菜';
-- 番茄
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 19 FROM ingredient WHERE name='番茄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='番茄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='番茄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.0 FROM ingredient WHERE name='番茄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='番茄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='番茄';
-- 白萝卜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 21 FROM ingredient WHERE name='白萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='白萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='白萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.0 FROM ingredient WHERE name='白萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='白萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 26 FROM ingredient WHERE name='白萝卜';
-- 空心菜  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 20 FROM ingredient WHERE name='空心菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.2 FROM ingredient WHERE name='空心菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='空心菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.6 FROM ingredient WHERE name='空心菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='空心菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='空心菜';
-- 红薯
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 86 FROM ingredient WHERE name='红薯';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.6 FROM ingredient WHERE name='红薯';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='红薯';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 20.1 FROM ingredient WHERE name='红薯';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.2 FROM ingredient WHERE name='红薯';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 77 FROM ingredient WHERE name='红薯';
-- 红辣椒  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 32 FROM ingredient WHERE name='红辣椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.3 FROM ingredient WHERE name='红辣椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='红辣椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.4 FROM ingredient WHERE name='红辣椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.2 FROM ingredient WHERE name='红辣椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='红辣椒';
-- 胡萝卜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 41 FROM ingredient WHERE name='胡萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='胡萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='胡萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 8.8 FROM ingredient WHERE name='胡萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.7 FROM ingredient WHERE name='胡萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 71 FROM ingredient WHERE name='胡萝卜';
-- 芋头
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 79 FROM ingredient WHERE name='芋头';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.2 FROM ingredient WHERE name='芋头';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='芋头';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 18.1 FROM ingredient WHERE name='芋头';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='芋头';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 48 FROM ingredient WHERE name='芋头';
-- 芹菜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 14 FROM ingredient WHERE name='芹菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='芹菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='芹菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.3 FROM ingredient WHERE name='芹菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='芹菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='芹菜';
-- 苋菜  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 25 FROM ingredient WHERE name='苋菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.8 FROM ingredient WHERE name='苋菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='苋菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='苋菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='苋菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='苋菜';
-- 苦瓜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='苦瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='苦瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='苦瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.9 FROM ingredient WHERE name='苦瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='苦瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 24 FROM ingredient WHERE name='苦瓜';
-- 茄子
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 21 FROM ingredient WHERE name='茄子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='茄子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='茄子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.9 FROM ingredient WHERE name='茄子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='茄子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='茄子';
-- 茼蒿
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 14 FROM ingredient WHERE name='茼蒿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.9 FROM ingredient WHERE name='茼蒿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='茼蒿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.7 FROM ingredient WHERE name='茼蒿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='茼蒿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='茼蒿';
-- 莲藕
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 73 FROM ingredient WHERE name='莲藕';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.7 FROM ingredient WHERE name='莲藕';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='莲藕';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 16.4 FROM ingredient WHERE name='莲藕';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.2 FROM ingredient WHERE name='莲藕';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 38 FROM ingredient WHERE name='莲藕';
-- 莴笋
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 14 FROM ingredient WHERE name='莴笋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='莴笋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='莴笋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.8 FROM ingredient WHERE name='莴笋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='莴笋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='莴笋';
-- 莴笋叶  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 20 FROM ingredient WHERE name='莴笋叶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.0 FROM ingredient WHERE name='莴笋叶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='莴笋叶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.5 FROM ingredient WHERE name='莴笋叶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='莴笋叶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='莴笋叶';
-- 菜花
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 24 FROM ingredient WHERE name='菜花';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.1 FROM ingredient WHERE name='菜花';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='菜花';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.6 FROM ingredient WHERE name='菜花';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='菜花';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='菜花';
-- 菠菜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 24 FROM ingredient WHERE name='菠菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.6 FROM ingredient WHERE name='菠菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='菠菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.5 FROM ingredient WHERE name='菠菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.8 FROM ingredient WHERE name='菠菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='菠菜';
-- 西芹  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 16 FROM ingredient WHERE name='西芹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='西芹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='西芹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.5 FROM ingredient WHERE name='西芹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='西芹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='西芹';
-- 西葫芦  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 19 FROM ingredient WHERE name='西葫芦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='西葫芦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='西葫芦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.8 FROM ingredient WHERE name='西葫芦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='西葫芦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='西葫芦';
-- 西蓝花
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 33 FROM ingredient WHERE name='西蓝花';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.1 FROM ingredient WHERE name='西蓝花';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='西蓝花';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.3 FROM ingredient WHERE name='西蓝花';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.7 FROM ingredient WHERE name='西蓝花';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='西蓝花';
-- 豆角  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 30 FROM ingredient WHERE name='豆角';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.7 FROM ingredient WHERE name='豆角';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='豆角';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.7 FROM ingredient WHERE name='豆角';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='豆角';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='豆角';
-- 豌豆
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 105 FROM ingredient WHERE name='豌豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.4 FROM ingredient WHERE name='豌豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='豌豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 21.2 FROM ingredient WHERE name='豌豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.0 FROM ingredient WHERE name='豌豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 42 FROM ingredient WHERE name='豌豆';
-- 豌豆苗  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 27 FROM ingredient WHERE name='豌豆苗';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.0 FROM ingredient WHERE name='豌豆苗';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.8 FROM ingredient WHERE name='豌豆苗';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.9 FROM ingredient WHERE name='豌豆苗';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='豌豆苗';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='豌豆苗';
-- 金针菇
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 32 FROM ingredient WHERE name='金针菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.4 FROM ingredient WHERE name='金针菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='金针菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.0 FROM ingredient WHERE name='金针菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='金针菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='金针菇';
-- 银耳(水发)  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 30 FROM ingredient WHERE name='银耳(水发)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='银耳(水发)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='银耳(水发)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.7 FROM ingredient WHERE name='银耳(水发)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='银耳(水发)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='银耳(水发)';
-- 青椒
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='青椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='青椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='青椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='青椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.2 FROM ingredient WHERE name='青椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='青椒';
-- 青萝卜  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 23 FROM ingredient WHERE name='青萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='青萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='青萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.2 FROM ingredient WHERE name='青萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='青萝卜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 26 FROM ingredient WHERE name='青萝卜';
-- 韭菜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='韭菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.4 FROM ingredient WHERE name='韭菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='韭菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.6 FROM ingredient WHERE name='韭菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='韭菜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='韭菜';
-- 鲜香菇
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='鲜香菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.2 FROM ingredient WHERE name='鲜香菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='鲜香菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.2 FROM ingredient WHERE name='鲜香菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='鲜香菇';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='鲜香菇';
-- 黄瓜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 16 FROM ingredient WHERE name='黄瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.8 FROM ingredient WHERE name='黄瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='黄瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.9 FROM ingredient WHERE name='黄瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.0 FROM ingredient WHERE name='黄瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 23 FROM ingredient WHERE name='黄瓜';
-- 黑木耳(水发)
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 21 FROM ingredient WHERE name='黑木耳(水发)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='黑木耳(水发)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='黑木耳(水发)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.0 FROM ingredient WHERE name='黑木耳(水发)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.1 FROM ingredient WHERE name='黑木耳(水发)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='黑木耳(水发)';
-- 五花肉
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 508 FROM ingredient WHERE name='五花肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.7 FROM ingredient WHERE name='五花肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 35.3 FROM ingredient WHERE name='五花肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='五花肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='五花肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='五花肉';
-- 牛肉(瘦)
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 106 FROM ingredient WHERE name='牛肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20.2 FROM ingredient WHERE name='牛肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.3 FROM ingredient WHERE name='牛肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.2 FROM ingredient WHERE name='牛肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='牛肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='牛肉(瘦)';
-- 牛腩
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 235 FROM ingredient WHERE name='牛腩';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.1 FROM ingredient WHERE name='牛腩';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 18.4 FROM ingredient WHERE name='牛腩';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='牛腩';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='牛腩';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='牛腩';
-- 猪大肠  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 196 FROM ingredient WHERE name='猪大肠';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 6.9 FROM ingredient WHERE name='猪大肠';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 18.7 FROM ingredient WHERE name='猪大肠';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='猪大肠';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪大肠';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪大肠';
-- 猪心  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 142 FROM ingredient WHERE name='猪心';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 15.5 FROM ingredient WHERE name='猪心';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 6.7 FROM ingredient WHERE name='猪心';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.8 FROM ingredient WHERE name='猪心';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪心';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪心';
-- 猪排骨
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 278 FROM ingredient WHERE name='猪排骨';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.3 FROM ingredient WHERE name='猪排骨';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 22.6 FROM ingredient WHERE name='猪排骨';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.7 FROM ingredient WHERE name='猪排骨';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪排骨';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪排骨';
-- 猪肉(瘦)
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 143 FROM ingredient WHERE name='猪肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20.3 FROM ingredient WHERE name='猪肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 6.2 FROM ingredient WHERE name='猪肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.5 FROM ingredient WHERE name='猪肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪肉(瘦)';
-- 猪肉(肥瘦)
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 395 FROM ingredient WHERE name='猪肉(肥瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 13.2 FROM ingredient WHERE name='猪肉(肥瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 37.0 FROM ingredient WHERE name='猪肉(肥瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.4 FROM ingredient WHERE name='猪肉(肥瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪肉(肥瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪肉(肥瘦)';
-- 猪肝
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 129 FROM ingredient WHERE name='猪肝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 19.3 FROM ingredient WHERE name='猪肝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.5 FROM ingredient WHERE name='猪肝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.0 FROM ingredient WHERE name='猪肝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪肝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪肝';
-- 猪蹄
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 260 FROM ingredient WHERE name='猪蹄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 22.6 FROM ingredient WHERE name='猪蹄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 18.8 FROM ingredient WHERE name='猪蹄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.6 FROM ingredient WHERE name='猪蹄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪蹄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪蹄';
-- 羊排  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 234 FROM ingredient WHERE name='羊排';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 16.5 FROM ingredient WHERE name='羊排';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 18.7 FROM ingredient WHERE name='羊排';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.5 FROM ingredient WHERE name='羊排';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='羊排';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='羊排';
-- 羊肉(瘦)
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 118 FROM ingredient WHERE name='羊肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20.5 FROM ingredient WHERE name='羊肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.9 FROM ingredient WHERE name='羊肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.2 FROM ingredient WHERE name='羊肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='羊肉(瘦)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='羊肉(瘦)';
-- 驴肉  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 116 FROM ingredient WHERE name='驴肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20.0 FROM ingredient WHERE name='驴肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.2 FROM ingredient WHERE name='驴肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.4 FROM ingredient WHERE name='驴肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='驴肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='驴肉';
-- 鸡爪  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 254 FROM ingredient WHERE name='鸡爪';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 23.9 FROM ingredient WHERE name='鸡爪';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 16.4 FROM ingredient WHERE name='鸡爪';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.7 FROM ingredient WHERE name='鸡爪';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.2 FROM ingredient WHERE name='鸡爪';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸡爪';
-- 鸡翅
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 194 FROM ingredient WHERE name='鸡翅';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.4 FROM ingredient WHERE name='鸡翅';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 11.8 FROM ingredient WHERE name='鸡翅';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.6 FROM ingredient WHERE name='鸡翅';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='鸡翅';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸡翅';
-- 鸡肉
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 167 FROM ingredient WHERE name='鸡肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 19.3 FROM ingredient WHERE name='鸡肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 9.4 FROM ingredient WHERE name='鸡肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.3 FROM ingredient WHERE name='鸡肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鸡肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸡肉';
-- 鸡胸肉
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 133 FROM ingredient WHERE name='鸡胸肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 19.4 FROM ingredient WHERE name='鸡胸肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 5.0 FROM ingredient WHERE name='鸡胸肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.5 FROM ingredient WHERE name='鸡胸肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鸡胸肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸡胸肉';
-- 鸡腿  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 181 FROM ingredient WHERE name='鸡腿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 16.0 FROM ingredient WHERE name='鸡腿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 13.0 FROM ingredient WHERE name='鸡腿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='鸡腿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鸡腿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸡腿';
-- 鸭肉
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 240 FROM ingredient WHERE name='鸭肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 15.5 FROM ingredient WHERE name='鸭肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 19.7 FROM ingredient WHERE name='鸭肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.2 FROM ingredient WHERE name='鸭肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鸭肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸭肉';
-- 鸭腿  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 222 FROM ingredient WHERE name='鸭腿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 16.0 FROM ingredient WHERE name='鸭腿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 17.5 FROM ingredient WHERE name='鸭腿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.6 FROM ingredient WHERE name='鸭腿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鸭腿';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸭腿';
-- 鹅肉  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 251 FROM ingredient WHERE name='鹅肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.9 FROM ingredient WHERE name='鹅肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 19.6 FROM ingredient WHERE name='鹅肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='鹅肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鹅肉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鹅肉';
-- 三文鱼
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 139 FROM ingredient WHERE name='三文鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.2 FROM ingredient WHERE name='三文鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 7.8 FROM ingredient WHERE name='三文鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='三文鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='三文鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='三文鱼';
-- 基围虾  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 101 FROM ingredient WHERE name='基围虾';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.2 FROM ingredient WHERE name='基围虾';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.4 FROM ingredient WHERE name='基围虾';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.9 FROM ingredient WHERE name='基围虾';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='基围虾';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='基围虾';
-- 墨鱼
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 81 FROM ingredient WHERE name='墨鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 15.2 FROM ingredient WHERE name='墨鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.9 FROM ingredient WHERE name='墨鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.4 FROM ingredient WHERE name='墨鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='墨鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='墨鱼';
-- 小龙虾  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 87 FROM ingredient WHERE name='小龙虾';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.9 FROM ingredient WHERE name='小龙虾';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.1 FROM ingredient WHERE name='小龙虾';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='小龙虾';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='小龙虾';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='小龙虾';
-- 带鱼
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 127 FROM ingredient WHERE name='带鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.7 FROM ingredient WHERE name='带鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 4.9 FROM ingredient WHERE name='带鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.1 FROM ingredient WHERE name='带鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='带鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='带鱼';
-- 扇贝  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 60 FROM ingredient WHERE name='扇贝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 11.1 FROM ingredient WHERE name='扇贝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='扇贝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.6 FROM ingredient WHERE name='扇贝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='扇贝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='扇贝';
-- 生蚝  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 73 FROM ingredient WHERE name='生蚝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5.3 FROM ingredient WHERE name='生蚝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.1 FROM ingredient WHERE name='生蚝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 8.2 FROM ingredient WHERE name='生蚝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='生蚝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='生蚝';
-- 章鱼  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 82 FROM ingredient WHERE name='章鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 14.9 FROM ingredient WHERE name='章鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.0 FROM ingredient WHERE name='章鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.1 FROM ingredient WHERE name='章鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='章鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='章鱼';
-- 紫菜(干)
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 207 FROM ingredient WHERE name='紫菜(干)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 26.7 FROM ingredient WHERE name='紫菜(干)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.1 FROM ingredient WHERE name='紫菜(干)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 44.1 FROM ingredient WHERE name='紫菜(干)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='紫菜(干)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='紫菜(干)';
-- 草鱼
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 113 FROM ingredient WHERE name='草鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 16.6 FROM ingredient WHERE name='草鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 5.2 FROM ingredient WHERE name='草鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='草鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='草鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='草鱼';
-- 虾仁
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 87 FROM ingredient WHERE name='虾仁';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.6 FROM ingredient WHERE name='虾仁';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.8 FROM ingredient WHERE name='虾仁';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.0 FROM ingredient WHERE name='虾仁';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='虾仁';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='虾仁';
-- 蛏子  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 65 FROM ingredient WHERE name='蛏子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 9.8 FROM ingredient WHERE name='蛏子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='蛏子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='蛏子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='蛏子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='蛏子';
-- 蛤蜊
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 62 FROM ingredient WHERE name='蛤蜊';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 10.1 FROM ingredient WHERE name='蛤蜊';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.1 FROM ingredient WHERE name='蛤蜊';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.8 FROM ingredient WHERE name='蛤蜊';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='蛤蜊';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='蛤蜊';
-- 螃蟹  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 95 FROM ingredient WHERE name='螃蟹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.5 FROM ingredient WHERE name='螃蟹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.6 FROM ingredient WHERE name='螃蟹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.3 FROM ingredient WHERE name='螃蟹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='螃蟹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='螃蟹';
-- 鱿鱼
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 75 FROM ingredient WHERE name='鱿鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.0 FROM ingredient WHERE name='鱿鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.8 FROM ingredient WHERE name='鱿鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='鱿鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鱿鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鱿鱼';
-- 鲈鱼
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 105 FROM ingredient WHERE name='鲈鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.6 FROM ingredient WHERE name='鲈鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.4 FROM ingredient WHERE name='鲈鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='鲈鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鲈鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鲈鱼';
-- 鲤鱼
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 109 FROM ingredient WHERE name='鲤鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.6 FROM ingredient WHERE name='鲤鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 4.1 FROM ingredient WHERE name='鲤鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.5 FROM ingredient WHERE name='鲤鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鲤鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鲤鱼';
-- 鲫鱼
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 108 FROM ingredient WHERE name='鲫鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.1 FROM ingredient WHERE name='鲫鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.7 FROM ingredient WHERE name='鲫鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.8 FROM ingredient WHERE name='鲫鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鲫鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鲫鱼';
-- 鲳鱼  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 142 FROM ingredient WHERE name='鲳鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.5 FROM ingredient WHERE name='鲳鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 7.3 FROM ingredient WHERE name='鲳鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='鲳鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鲳鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鲳鱼';
-- 鳕鱼  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 88 FROM ingredient WHERE name='鳕鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20.4 FROM ingredient WHERE name='鳕鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.5 FROM ingredient WHERE name='鳕鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.5 FROM ingredient WHERE name='鳕鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鳕鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鳕鱼';
-- 黄花鱼
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 99 FROM ingredient WHERE name='黄花鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.6 FROM ingredient WHERE name='黄花鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.5 FROM ingredient WHERE name='黄花鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.8 FROM ingredient WHERE name='黄花鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='黄花鱼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='黄花鱼';
-- 咸鸭蛋
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 190 FROM ingredient WHERE name='咸鸭蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 12.7 FROM ingredient WHERE name='咸鸭蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 12.7 FROM ingredient WHERE name='咸鸭蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.3 FROM ingredient WHERE name='咸鸭蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='咸鸭蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='咸鸭蛋';
-- 皮蛋
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 171 FROM ingredient WHERE name='皮蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 14.2 FROM ingredient WHERE name='皮蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 10.7 FROM ingredient WHERE name='皮蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.5 FROM ingredient WHERE name='皮蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='皮蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='皮蛋';
-- 鸡蛋
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 144 FROM ingredient WHERE name='鸡蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 13.3 FROM ingredient WHERE name='鸡蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 8.8 FROM ingredient WHERE name='鸡蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.8 FROM ingredient WHERE name='鸡蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.5 FROM ingredient WHERE name='鸡蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='鸡蛋';
-- 鸭蛋
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 180 FROM ingredient WHERE name='鸭蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 12.6 FROM ingredient WHERE name='鸭蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 13.0 FROM ingredient WHERE name='鸭蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.1 FROM ingredient WHERE name='鸭蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='鸭蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='鸭蛋';
-- 鸽蛋  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 168 FROM ingredient WHERE name='鸽蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 10.8 FROM ingredient WHERE name='鸽蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 12.0 FROM ingredient WHERE name='鸽蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.0 FROM ingredient WHERE name='鸽蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='鸽蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='鸽蛋';
-- 鹌鹑蛋
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 160 FROM ingredient WHERE name='鹌鹑蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 12.8 FROM ingredient WHERE name='鹌鹑蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 11.1 FROM ingredient WHERE name='鹌鹑蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.1 FROM ingredient WHERE name='鹌鹑蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='鹌鹑蛋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='鹌鹑蛋';
-- 内酯豆腐  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 49 FROM ingredient WHERE name='内酯豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5.0 FROM ingredient WHERE name='内酯豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.9 FROM ingredient WHERE name='内酯豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.3 FROM ingredient WHERE name='内酯豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='内酯豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='内酯豆腐';
-- 北豆腐
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 98 FROM ingredient WHERE name='北豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 12.2 FROM ingredient WHERE name='北豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 4.8 FROM ingredient WHERE name='北豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.5 FROM ingredient WHERE name='北豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='北豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='北豆腐';
-- 南豆腐
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 57 FROM ingredient WHERE name='南豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 6.2 FROM ingredient WHERE name='南豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.5 FROM ingredient WHERE name='南豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.4 FROM ingredient WHERE name='南豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='南豆腐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='南豆腐';
-- 毛豆
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 123 FROM ingredient WHERE name='毛豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 13.1 FROM ingredient WHERE name='毛豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 5.0 FROM ingredient WHERE name='毛豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 10.5 FROM ingredient WHERE name='毛豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.2 FROM ingredient WHERE name='毛豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 18 FROM ingredient WHERE name='毛豆';
-- 红豆
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 309 FROM ingredient WHERE name='红豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20.2 FROM ingredient WHERE name='红豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='红豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 63.4 FROM ingredient WHERE name='红豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.0 FROM ingredient WHERE name='红豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 23 FROM ingredient WHERE name='红豆';
-- 绿豆
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 316 FROM ingredient WHERE name='绿豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 21.6 FROM ingredient WHERE name='绿豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.8 FROM ingredient WHERE name='绿豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 62.0 FROM ingredient WHERE name='绿豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.0 FROM ingredient WHERE name='绿豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 27 FROM ingredient WHERE name='绿豆';
-- 腐竹
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 459 FROM ingredient WHERE name='腐竹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 44.6 FROM ingredient WHERE name='腐竹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 21.7 FROM ingredient WHERE name='腐竹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 22.3 FROM ingredient WHERE name='腐竹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='腐竹';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='腐竹';
-- 豆浆
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 31 FROM ingredient WHERE name='豆浆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.0 FROM ingredient WHERE name='豆浆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.6 FROM ingredient WHERE name='豆浆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.2 FROM ingredient WHERE name='豆浆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='豆浆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='豆浆';
-- 豆腐(北)
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 98 FROM ingredient WHERE name='豆腐(北)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 12.2 FROM ingredient WHERE name='豆腐(北)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 4.8 FROM ingredient WHERE name='豆腐(北)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.5 FROM ingredient WHERE name='豆腐(北)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='豆腐(北)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='豆腐(北)';
-- 豆腐(嫩)
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 57 FROM ingredient WHERE name='豆腐(嫩)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 6.2 FROM ingredient WHERE name='豆腐(嫩)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.5 FROM ingredient WHERE name='豆腐(嫩)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.4 FROM ingredient WHERE name='豆腐(嫩)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='豆腐(嫩)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='豆腐(嫩)';
-- 豆腐干
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 140 FROM ingredient WHERE name='豆腐干';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 16.2 FROM ingredient WHERE name='豆腐干';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.6 FROM ingredient WHERE name='豆腐干';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 11.5 FROM ingredient WHERE name='豆腐干';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='豆腐干';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='豆腐干';
-- 豆腐皮
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 409 FROM ingredient WHERE name='豆腐皮';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 44.6 FROM ingredient WHERE name='豆腐皮';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 17.4 FROM ingredient WHERE name='豆腐皮';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 18.8 FROM ingredient WHERE name='豆腐皮';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.3 FROM ingredient WHERE name='豆腐皮';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='豆腐皮';
-- 黄豆
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 390 FROM ingredient WHERE name='黄豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 35.0 FROM ingredient WHERE name='黄豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 16.0 FROM ingredient WHERE name='黄豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 34.2 FROM ingredient WHERE name='黄豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 5.0 FROM ingredient WHERE name='黄豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 18 FROM ingredient WHERE name='黄豆';
-- 黑豆
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 381 FROM ingredient WHERE name='黑豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 36.0 FROM ingredient WHERE name='黑豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 15.9 FROM ingredient WHERE name='黑豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 33.6 FROM ingredient WHERE name='黑豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.0 FROM ingredient WHERE name='黑豆';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 18 FROM ingredient WHERE name='黑豆';
-- 奶粉  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 478 FROM ingredient WHERE name='奶粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 24.0 FROM ingredient WHERE name='奶粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 22.0 FROM ingredient WHERE name='奶粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 52.0 FROM ingredient WHERE name='奶粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 36.0 FROM ingredient WHERE name='奶粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 27 FROM ingredient WHERE name='奶粉';
-- 奶酪  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 328 FROM ingredient WHERE name='奶酪';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 25.7 FROM ingredient WHERE name='奶酪';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 23.5 FROM ingredient WHERE name='奶酪';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.5 FROM ingredient WHERE name='奶酪';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='奶酪';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='奶酪';
-- 淡奶油  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 345 FROM ingredient WHERE name='淡奶油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.2 FROM ingredient WHERE name='淡奶油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 35.0 FROM ingredient WHERE name='淡奶油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.0 FROM ingredient WHERE name='淡奶油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.0 FROM ingredient WHERE name='淡奶油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='淡奶油';
-- 炼乳  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 332 FROM ingredient WHERE name='炼乳';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.5 FROM ingredient WHERE name='炼乳';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 8.0 FROM ingredient WHERE name='炼乳';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 56.0 FROM ingredient WHERE name='炼乳';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 54.0 FROM ingredient WHERE name='炼乳';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 35 FROM ingredient WHERE name='炼乳';
-- 牛奶
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 54 FROM ingredient WHERE name='牛奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.0 FROM ingredient WHERE name='牛奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.2 FROM ingredient WHERE name='牛奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.4 FROM ingredient WHERE name='牛奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='牛奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 27 FROM ingredient WHERE name='牛奶';
-- 羊奶
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 59 FROM ingredient WHERE name='羊奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.8 FROM ingredient WHERE name='羊奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.5 FROM ingredient WHERE name='羊奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='羊奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='羊奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 27 FROM ingredient WHERE name='羊奶';
-- 酸奶
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 72 FROM ingredient WHERE name='酸奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.5 FROM ingredient WHERE name='酸奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.7 FROM ingredient WHERE name='酸奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 9.3 FROM ingredient WHERE name='酸奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 7.0 FROM ingredient WHERE name='酸奶';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 48 FROM ingredient WHERE name='酸奶';
-- 黄油
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 717 FROM ingredient WHERE name='黄油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='黄油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 81.0 FROM ingredient WHERE name='黄油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.1 FROM ingredient WHERE name='黄油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='黄油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='黄油';
-- 五香粉  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 280 FROM ingredient WHERE name='五香粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.0 FROM ingredient WHERE name='五香粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 8.0 FROM ingredient WHERE name='五香粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 55.0 FROM ingredient WHERE name='五香粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.0 FROM ingredient WHERE name='五香粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='五香粉';
-- 八角  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 195 FROM ingredient WHERE name='八角';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.8 FROM ingredient WHERE name='八角';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 5.6 FROM ingredient WHERE name='八角';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 35.0 FROM ingredient WHERE name='八角';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='八角';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='八角';
-- 冰糖
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 397 FROM ingredient WHERE name='冰糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.1 FROM ingredient WHERE name='冰糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='冰糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 98.5 FROM ingredient WHERE name='冰糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 98.5 FROM ingredient WHERE name='冰糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='冰糖';
-- 干辣椒  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 212 FROM ingredient WHERE name='干辣椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 15.0 FROM ingredient WHERE name='干辣椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 12.0 FROM ingredient WHERE name='干辣椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 9.0 FROM ingredient WHERE name='干辣椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 5.0 FROM ingredient WHERE name='干辣椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='干辣椒';
-- 料酒  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 80 FROM ingredient WHERE name='料酒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.3 FROM ingredient WHERE name='料酒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='料酒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.0 FROM ingredient WHERE name='料酒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='料酒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='料酒';
-- 桂皮  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 247 FROM ingredient WHERE name='桂皮';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.0 FROM ingredient WHERE name='桂皮';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.2 FROM ingredient WHERE name='桂皮';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 80.6 FROM ingredient WHERE name='桂皮';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.2 FROM ingredient WHERE name='桂皮';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='桂皮';
-- 橄榄油
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 899 FROM ingredient WHERE name='橄榄油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='橄榄油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='橄榄油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='橄榄油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='橄榄油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='橄榄油';
-- 淀粉
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 349 FROM ingredient WHERE name='淀粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.3 FROM ingredient WHERE name='淀粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='淀粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 86.6 FROM ingredient WHERE name='淀粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='淀粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='淀粉';
-- 猪油
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 897 FROM ingredient WHERE name='猪油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='猪油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.6 FROM ingredient WHERE name='猪油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.2 FROM ingredient WHERE name='猪油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪油';
-- 甜面酱  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 136 FROM ingredient WHERE name='甜面酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5.5 FROM ingredient WHERE name='甜面酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='甜面酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 28.5 FROM ingredient WHERE name='甜面酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 18.0 FROM ingredient WHERE name='甜面酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='甜面酱';
-- 番茄酱
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 81 FROM ingredient WHERE name='番茄酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.9 FROM ingredient WHERE name='番茄酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='番茄酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 16.9 FROM ingredient WHERE name='番茄酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 14.9 FROM ingredient WHERE name='番茄酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='番茄酱';
-- 白糖
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 400 FROM ingredient WHERE name='白糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='白糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='白糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 99.9 FROM ingredient WHERE name='白糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 99.9 FROM ingredient WHERE name='白糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='白糖';
-- 白胡椒  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 255 FROM ingredient WHERE name='白胡椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 9.4 FROM ingredient WHERE name='白胡椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.0 FROM ingredient WHERE name='白胡椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 65.0 FROM ingredient WHERE name='白胡椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='白胡椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='白胡椒';
-- 红糖  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 389 FROM ingredient WHERE name='红糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.7 FROM ingredient WHERE name='红糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='红糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 96.6 FROM ingredient WHERE name='红糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 92.0 FROM ingredient WHERE name='红糖';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='红糖';
-- 芝麻油
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 898 FROM ingredient WHERE name='芝麻油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='芝麻油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.7 FROM ingredient WHERE name='芝麻油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.2 FROM ingredient WHERE name='芝麻油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='芝麻油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='芝麻油';
-- 花椒  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 258 FROM ingredient WHERE name='花椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 6.7 FROM ingredient WHERE name='花椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 8.9 FROM ingredient WHERE name='花椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 56.6 FROM ingredient WHERE name='花椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='花椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='花椒';
-- 花生油
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 899 FROM ingredient WHERE name='花生油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='花生油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='花生油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='花生油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='花生油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='花生油';
-- 菜籽油
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 899 FROM ingredient WHERE name='菜籽油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='菜籽油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='菜籽油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='菜籽油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='菜籽油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='菜籽油';
-- 蚝油  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 82 FROM ingredient WHERE name='蚝油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.5 FROM ingredient WHERE name='蚝油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='蚝油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 16.0 FROM ingredient WHERE name='蚝油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 12.0 FROM ingredient WHERE name='蚝油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='蚝油';
-- 豆瓣酱  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 178 FROM ingredient WHERE name='豆瓣酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 8.0 FROM ingredient WHERE name='豆瓣酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 6.8 FROM ingredient WHERE name='豆瓣酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 19.0 FROM ingredient WHERE name='豆瓣酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.0 FROM ingredient WHERE name='豆瓣酱';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='豆瓣酱';
-- 酱油(生抽)
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 63 FROM ingredient WHERE name='酱油(生抽)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5.6 FROM ingredient WHERE name='酱油(生抽)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='酱油(生抽)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.1 FROM ingredient WHERE name='酱油(生抽)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='酱油(生抽)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='酱油(生抽)';
-- 酱油(老抽)  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 65 FROM ingredient WHERE name='酱油(老抽)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 6.0 FROM ingredient WHERE name='酱油(老抽)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='酱油(老抽)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.0 FROM ingredient WHERE name='酱油(老抽)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='酱油(老抽)';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='酱油(老抽)';
-- 醋
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 31 FROM ingredient WHERE name='醋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.1 FROM ingredient WHERE name='醋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='醋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.9 FROM ingredient WHERE name='醋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='醋';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='醋';
-- 食用油
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 899 FROM ingredient WHERE name='食用油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='食用油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='食用油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='食用油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='食用油';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='食用油';
-- 食盐
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 0 FROM ingredient WHERE name='食盐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='食盐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='食盐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='食盐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='食盐';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='食盐';
-- 黑胡椒  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 255 FROM ingredient WHERE name='黑胡椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 10.0 FROM ingredient WHERE name='黑胡椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.0 FROM ingredient WHERE name='黑胡椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 64.0 FROM ingredient WHERE name='黑胡椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='黑胡椒';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='黑胡椒';
-- 哈密瓜  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 34 FROM ingredient WHERE name='哈密瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.5 FROM ingredient WHERE name='哈密瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='哈密瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.7 FROM ingredient WHERE name='哈密瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 6.0 FROM ingredient WHERE name='哈密瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 65 FROM ingredient WHERE name='哈密瓜';
-- 提子  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 50 FROM ingredient WHERE name='提子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.5 FROM ingredient WHERE name='提子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='提子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 12.0 FROM ingredient WHERE name='提子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 10.0 FROM ingredient WHERE name='提子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 46 FROM ingredient WHERE name='提子';
-- 柚子
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 42 FROM ingredient WHERE name='柚子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.8 FROM ingredient WHERE name='柚子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='柚子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 9.5 FROM ingredient WHERE name='柚子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.8 FROM ingredient WHERE name='柚子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 25 FROM ingredient WHERE name='柚子';
-- 桃
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 51 FROM ingredient WHERE name='桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 12.2 FROM ingredient WHERE name='桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 8.0 FROM ingredient WHERE name='桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='桃';
-- 梨
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 51 FROM ingredient WHERE name='梨';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.3 FROM ingredient WHERE name='梨';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='梨';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 13.3 FROM ingredient WHERE name='梨';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 7.0 FROM ingredient WHERE name='梨';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 36 FROM ingredient WHERE name='梨';
-- 榴莲
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 147 FROM ingredient WHERE name='榴莲';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='榴莲';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.3 FROM ingredient WHERE name='榴莲';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 27.0 FROM ingredient WHERE name='榴莲';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 14.0 FROM ingredient WHERE name='榴莲';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 49 FROM ingredient WHERE name='榴莲';
-- 樱桃
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 46 FROM ingredient WHERE name='樱桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='樱桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='樱桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 10.2 FROM ingredient WHERE name='樱桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 8.0 FROM ingredient WHERE name='樱桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 22 FROM ingredient WHERE name='樱桃';
-- 橘子
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 47 FROM ingredient WHERE name='橘子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='橘子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='橘子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 11.6 FROM ingredient WHERE name='橘子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 8.8 FROM ingredient WHERE name='橘子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 43 FROM ingredient WHERE name='橘子';
-- 橙子
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 48 FROM ingredient WHERE name='橙子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.8 FROM ingredient WHERE name='橙子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='橙子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 11.1 FROM ingredient WHERE name='橙子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 9.5 FROM ingredient WHERE name='橙子';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 43 FROM ingredient WHERE name='橙子';
-- 火龙果  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 51 FROM ingredient WHERE name='火龙果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='火龙果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='火龙果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 13.3 FROM ingredient WHERE name='火龙果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 8.0 FROM ingredient WHERE name='火龙果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 25 FROM ingredient WHERE name='火龙果';
-- 猕猴桃
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 61 FROM ingredient WHERE name='猕猴桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.8 FROM ingredient WHERE name='猕猴桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='猕猴桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 14.5 FROM ingredient WHERE name='猕猴桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 9.2 FROM ingredient WHERE name='猕猴桃';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 50 FROM ingredient WHERE name='猕猴桃';
-- 石榴  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 72 FROM ingredient WHERE name='石榴';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='石榴';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='石榴';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 18.7 FROM ingredient WHERE name='石榴';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 13.0 FROM ingredient WHERE name='石榴';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 35 FROM ingredient WHERE name='石榴';
-- 芒果  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 35 FROM ingredient WHERE name='芒果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.6 FROM ingredient WHERE name='芒果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='芒果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 8.3 FROM ingredient WHERE name='芒果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 7.0 FROM ingredient WHERE name='芒果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 55 FROM ingredient WHERE name='芒果';
-- 苹果
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 54 FROM ingredient WHERE name='苹果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.2 FROM ingredient WHERE name='苹果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='苹果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 13.5 FROM ingredient WHERE name='苹果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 9.3 FROM ingredient WHERE name='苹果';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 36 FROM ingredient WHERE name='苹果';
-- 草莓
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 32 FROM ingredient WHERE name='草莓';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.0 FROM ingredient WHERE name='草莓';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='草莓';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.1 FROM ingredient WHERE name='草莓';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.7 FROM ingredient WHERE name='草莓';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 29 FROM ingredient WHERE name='草莓';
-- 荔枝
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 70 FROM ingredient WHERE name='荔枝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='荔枝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='荔枝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 16.6 FROM ingredient WHERE name='荔枝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 14.0 FROM ingredient WHERE name='荔枝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 50 FROM ingredient WHERE name='荔枝';
-- 菠萝
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 44 FROM ingredient WHERE name='菠萝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.5 FROM ingredient WHERE name='菠萝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='菠萝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 10.8 FROM ingredient WHERE name='菠萝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 9.9 FROM ingredient WHERE name='菠萝';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 66 FROM ingredient WHERE name='菠萝';
-- 葡萄
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 44 FROM ingredient WHERE name='葡萄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.5 FROM ingredient WHERE name='葡萄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='葡萄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 10.3 FROM ingredient WHERE name='葡萄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 8.8 FROM ingredient WHERE name='葡萄';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 43 FROM ingredient WHERE name='葡萄';
-- 蓝莓  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 57 FROM ingredient WHERE name='蓝莓';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.7 FROM ingredient WHERE name='蓝莓';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='蓝莓';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 14.5 FROM ingredient WHERE name='蓝莓';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 10.0 FROM ingredient WHERE name='蓝莓';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 53 FROM ingredient WHERE name='蓝莓';
-- 西瓜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='西瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.6 FROM ingredient WHERE name='西瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='西瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.8 FROM ingredient WHERE name='西瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 5.2 FROM ingredient WHERE name='西瓜';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 72 FROM ingredient WHERE name='西瓜';
-- 香蕉
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 93 FROM ingredient WHERE name='香蕉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='香蕉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='香蕉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 22.0 FROM ingredient WHERE name='香蕉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 12.2 FROM ingredient WHERE name='香蕉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 52 FROM ingredient WHERE name='香蕉';
-- 龙眼  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 71 FROM ingredient WHERE name='龙眼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='龙眼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='龙眼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 16.6 FROM ingredient WHERE name='龙眼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 14.0 FROM ingredient WHERE name='龙眼';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 50 FROM ingredient WHERE name='龙眼';
-- 大米
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 346 FROM ingredient WHERE name='大米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.4 FROM ingredient WHERE name='大米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.8 FROM ingredient WHERE name='大米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 77.9 FROM ingredient WHERE name='大米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='大米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='大米';
-- 小米
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 358 FROM ingredient WHERE name='小米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 9.0 FROM ingredient WHERE name='小米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.1 FROM ingredient WHERE name='小米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 75.1 FROM ingredient WHERE name='小米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='小米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 71 FROM ingredient WHERE name='小米';
-- 挂面
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 344 FROM ingredient WHERE name='挂面';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 9.6 FROM ingredient WHERE name='挂面';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.7 FROM ingredient WHERE name='挂面';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 75.1 FROM ingredient WHERE name='挂面';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='挂面';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 82 FROM ingredient WHERE name='挂面';
-- 燕麦
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 367 FROM ingredient WHERE name='燕麦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 15.0 FROM ingredient WHERE name='燕麦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 6.7 FROM ingredient WHERE name='燕麦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 66.9 FROM ingredient WHERE name='燕麦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='燕麦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 55 FROM ingredient WHERE name='燕麦';
-- 玉米面
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 341 FROM ingredient WHERE name='玉米面';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 8.1 FROM ingredient WHERE name='玉米面';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.3 FROM ingredient WHERE name='玉米面';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 75.2 FROM ingredient WHERE name='玉米面';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='玉米面';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 68 FROM ingredient WHERE name='玉米面';
-- 米饭
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 116 FROM ingredient WHERE name='米饭';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.6 FROM ingredient WHERE name='米饭';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='米饭';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 25.9 FROM ingredient WHERE name='米饭';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='米饭';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 83 FROM ingredient WHERE name='米饭';
-- 糯米
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 350 FROM ingredient WHERE name='糯米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.3 FROM ingredient WHERE name='糯米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.0 FROM ingredient WHERE name='糯米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 78.3 FROM ingredient WHERE name='糯米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='糯米';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='糯米';
-- 荞麦
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 337 FROM ingredient WHERE name='荞麦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 9.3 FROM ingredient WHERE name='荞麦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.3 FROM ingredient WHERE name='荞麦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 73.0 FROM ingredient WHERE name='荞麦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='荞麦';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 54 FROM ingredient WHERE name='荞麦';
-- 面粉
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 350 FROM ingredient WHERE name='面粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 11.2 FROM ingredient WHERE name='面粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.5 FROM ingredient WHERE name='面粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 73.6 FROM ingredient WHERE name='面粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='面粉';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='面粉';
-- 馒头
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 223 FROM ingredient WHERE name='馒头';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.0 FROM ingredient WHERE name='馒头';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.1 FROM ingredient WHERE name='馒头';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 47.0 FROM ingredient WHERE name='馒头';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.0 FROM ingredient WHERE name='馒头';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 88 FROM ingredient WHERE name='馒头';
-- 鲜面条  -- 【估】参考估值
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 280 FROM ingredient WHERE name='鲜面条';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 8.0 FROM ingredient WHERE name='鲜面条';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.5 FROM ingredient WHERE name='鲜面条';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 60.0 FROM ingredient WHERE name='鲜面条';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鲜面条';
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 82 FROM ingredient WHERE name='鲜面条';

COMMIT;

-- 验证：SELECT COUNT(*) FROM ingredient; (目标 150-200)
-- 抽查 UTF-8：SELECT name,HEX(name) FROM ingredient WHERE name='牛肉(瘦)';  -- 应为 E7 89 9B ... 非 0001