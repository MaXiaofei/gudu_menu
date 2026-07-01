-- ============================================================
-- V25__ingredients_ext2.sql
-- 咕嘟小食单：细分品类扩充食材 259 种 + 营养 EAV(6 项/食材)
--   辣椒/番茄/白菜/萝卜/瓜/菌菇/绿叶/葱姜蒜/豆/畜禽肉/内脏血/水产/
--   蛋/豆制品/乳/调味料/油/水果/谷物主食/海藻 细分，覆盖 V23 未收录项
-- 营养数据参考《中国食物成分表》(per 100g)；标【估】为同品类典型参考估值
-- metric_id: 1=calorie(kcal) 2=protein(g) 3=fat(g) 4=carb(g) 5=sugar(g) 6=gi
-- 幂等：按 name 去重 INSERT(不 DELETE)，避免破坏 dish_ingredient 外键
--   食材：INSERT...SELECT ... WHERE NOT EXISTS(name) 已存在则跳过
--   营养：INSERT IGNORE(uk_ing_metric 唯一约束兜底)
--   绝不 DELETE FROM ingredient(参照 V23 治本后的写法)
-- price=0（已移除价格展示）；UTF-8
-- ============================================================

START TRANSACTION;

-- 1) 批量插入食材(按 name 去重：已存在则跳过，不删不破坏外键)
INSERT INTO ingredient(name, unit_id, price, purchase_category_id)
SELECT v.name, v.unit_id, v.price, v.purchase_category_id
FROM (
  VALUES
  ROW('小米椒', 20, 0, 24),
  ROW('杭椒', 20, 0, 24),
  ROW('二荆条', 20, 0, 24),
  ROW('螺丝椒', 20, 0, 24),
  ROW('线椒', 20, 0, 24),
  ROW('朝天椒', 20, 0, 24),
  ROW('灯笼椒', 20, 0, 24),
  ROW('牛角椒', 20, 0, 24),
  ROW('羊角椒', 20, 0, 24),
  ROW('彩椒(红)', 20, 0, 24),
  ROW('彩椒(黄)', 20, 0, 24),
  ROW('彩椒(橙)', 20, 0, 24),
  ROW('青尖椒', 20, 0, 24),
  ROW('红尖椒', 20, 0, 24),
  ROW('美人椒', 20, 0, 24),
  ROW('黄灯笼椒', 20, 0, 24),
  ROW('泡椒', 20, 0, 24),
  ROW('圣女果', 22, 0, 24),
  ROW('樱桃番茄', 22, 0, 24),
  ROW('牛番茄', 20, 0, 24),
  ROW('水果番茄', 20, 0, 24),
  ROW('茄子(长)', 20, 0, 24),
  ROW('茄子(圆)', 20, 0, 24),
  ROW('绿茄子', 20, 0, 24),
  ROW('天津白', 20, 0, 24),
  ROW('快菜', 20, 0, 24),
  ROW('菜心', 20, 0, 24),
  ROW('奶白菜', 20, 0, 24),
  ROW('红萝卜', 20, 0, 24),
  ROW('心里美', 20, 0, 24),
  ROW('水萝卜', 20, 0, 24),
  ROW('樱桃萝卜', 20, 0, 24),
  ROW('佛手瓜', 20, 0, 24),
  ROW('蛇瓜', 20, 0, 24),
  ROW('瓠瓜', 20, 0, 24),
  ROW('越瓜', 20, 0, 24),
  ROW('香菇', 20, 0, 24),
  ROW('口蘑', 20, 0, 24),
  ROW('蟹味菇', 20, 0, 24),
  ROW('白玉菇', 20, 0, 24),
  ROW('松茸', 20, 0, 24),
  ROW('鸡枞菌', 20, 0, 24),
  ROW('木耳(干)', 20, 0, 24),
  ROW('银耳(干)', 20, 0, 24),
  ROW('竹荪', 20, 0, 24),
  ROW('猴头菇', 20, 0, 24),
  ROW('虫草花', 20, 0, 24),
  ROW('茶树菇', 20, 0, 24),
  ROW('生菜(圆)', 20, 0, 24),
  ROW('生菜(罗曼)', 20, 0, 24),
  ROW('罗马生菜', 20, 0, 24),
  ROW('结球生菜', 20, 0, 24),
  ROW('奶油生菜', 20, 0, 24),
  ROW('苋菜(红)', 20, 0, 24),
  ROW('苋菜(绿)', 20, 0, 24),
  ROW('荠菜', 20, 0, 24),
  ROW('韭黄', 20, 0, 24),
  ROW('蒜薹', 20, 0, 24),
  ROW('蒜苗', 20, 0, 24),
  ROW('枸杞叶', 20, 0, 24),
  ROW('红薯叶', 20, 0, 24),
  ROW('木耳菜', 20, 0, 24),
  ROW('紫苏', 20, 0, 24),
  ROW('薄荷', 20, 0, 24),
  ROW('九层塔', 20, 0, 24),
  ROW('罗勒', 20, 0, 24),
  ROW('迷迭香', 20, 0, 24),
  ROW('百里香', 20, 0, 24),
  ROW('香菜', 20, 0, 24),
  ROW('香椿', 20, 0, 24),
  ROW('马齿苋', 20, 0, 24),
  ROW('蒲公英', 20, 0, 24),
  ROW('鱼腥草', 20, 0, 24),
  ROW('紫菜薹', 20, 0, 24),
  ROW('红菜薹', 20, 0, 24),
  ROW('芥蓝', 20, 0, 24),
  ROW('芥菜', 20, 0, 24),
  ROW('雪里蕻', 20, 0, 24),
  ROW('芝麻菜', 20, 0, 24),
  ROW('塔菜', 20, 0, 24),
  ROW('乌塌菜', 20, 0, 24),
  ROW('秋葵', 20, 0, 24),
  ROW('芦笋', 20, 0, 24),
  ROW('竹笋', 20, 0, 24),
  ROW('春笋', 20, 0, 24),
  ROW('冬笋', 20, 0, 24),
  ROW('茭白', 20, 0, 24),
  ROW('菱角', 20, 0, 24),
  ROW('荸荠', 20, 0, 24),
  ROW('慈姑', 20, 0, 24),
  ROW('魔芋', 20, 0, 24),
  ROW('百合', 20, 0, 24),
  ROW('莲子', 20, 0, 24),
  ROW('黄花菜', 20, 0, 24),
  ROW('榨菜', 20, 0, 24),
  ROW('大头菜', 20, 0, 24),
  ROW('苤蓝', 20, 0, 24),
  ROW('甜菜根', 20, 0, 24),
  ROW('菊芋', 20, 0, 24),
  ROW('凉薯', 20, 0, 24),
  ROW('贡菜', 20, 0, 24),
  ROW('香葱', 20, 0, 24),
  ROW('洋葱(紫)', 20, 0, 24),
  ROW('洋葱(黄)', 20, 0, 24),
  ROW('洋葱(白)', 20, 0, 24),
  ROW('蒜瓣', 20, 0, 24),
  ROW('仔姜', 20, 0, 24),
  ROW('韭菜花', 20, 0, 24),
  ROW('四季豆', 20, 0, 24),
  ROW('豌豆荚', 20, 0, 24),
  ROW('黄豆芽', 20, 0, 24),
  ROW('绿豆芽', 20, 0, 24),
  ROW('豇豆', 20, 0, 24),
  ROW('蚕豆', 20, 0, 24),
  ROW('刀豆', 20, 0, 24),
  ROW('猪里脊', 20, 0, 25),
  ROW('猪舌', 20, 0, 25),
  ROW('腊肉', 20, 0, 25),
  ROW('火腿', 20, 0, 25),
  ROW('香肠', 20, 0, 25),
  ROW('培根', 20, 0, 25),
  ROW('牛里脊', 20, 0, 25),
  ROW('牛腱', 20, 0, 25),
  ROW('牛排', 20, 0, 25),
  ROW('羊肉', 20, 0, 25),
  ROW('羊腿', 20, 0, 25),
  ROW('猪肚', 20, 0, 25),
  ROW('猪血', 20, 0, 25),
  ROW('鸭血', 20, 0, 25),
  ROW('鸡血', 20, 0, 25),
  ROW('牛百叶', 20, 0, 25),
  ROW('毛肚', 20, 0, 25),
  ROW('黄喉', 20, 0, 25),
  ROW('鳙鱼', 20, 0, 26),
  ROW('鲢鱼', 20, 0, 26),
  ROW('金枪鱼', 20, 0, 26),
  ROW('沙丁鱼', 20, 0, 26),
  ROW('鳗鱼', 20, 0, 26),
  ROW('鲶鱼', 20, 0, 26),
  ROW('黑鱼', 20, 0, 26),
  ROW('桂鱼', 20, 0, 26),
  ROW('马鲛鱼', 20, 0, 26),
  ROW('鲅鱼', 20, 0, 26),
  ROW('秋刀鱼', 20, 0, 26),
  ROW('多春鱼', 20, 0, 26),
  ROW('银鱼', 20, 0, 26),
  ROW('罗非鱼', 20, 0, 26),
  ROW('鳊鱼', 20, 0, 26),
  ROW('泥鳅', 20, 0, 26),
  ROW('鳝鱼', 20, 0, 26),
  ROW('皮皮虾', 20, 0, 26),
  ROW('大闸蟹', 22, 0, 26),
  ROW('梭子蟹', 22, 0, 26),
  ROW('青蟹', 22, 0, 26),
  ROW('龙虾', 20, 0, 26),
  ROW('北极虾', 20, 0, 26),
  ROW('虾米', 20, 0, 26),
  ROW('虾皮', 20, 0, 26),
  ROW('干贝', 20, 0, 26),
  ROW('瑶柱', 20, 0, 26),
  ROW('鲍鱼', 20, 0, 26),
  ROW('海参', 20, 0, 26),
  ROW('花甲', 20, 0, 26),
  ROW('蚬子', 20, 0, 26),
  ROW('蛏子王', 20, 0, 26),
  ROW('青口贝', 20, 0, 26),
  ROW('文蛤', 20, 0, 26),
  ROW('花蛤', 20, 0, 26),
  ROW('北极贝', 20, 0, 26),
  ROW('海螺', 20, 0, 26),
  ROW('田螺', 20, 0, 26),
  ROW('海带(干)', 20, 0, 26),
  ROW('裙带菜', 20, 0, 26),
  ROW('海蜇', 20, 0, 26),
  ROW('海蜇皮', 20, 0, 26),
  ROW('鸽子蛋', 22, 0, 27),
  ROW('鹅蛋', 22, 0, 27),
  ROW('茶叶蛋', 22, 0, 27),
  ROW('豆腐丝', 20, 0, 28),
  ROW('千张', 20, 0, 28),
  ROW('油豆腐', 20, 0, 28),
  ROW('毛豆腐', 20, 0, 28),
  ROW('臭豆腐', 20, 0, 28),
  ROW('豆筋', 20, 0, 28),
  ROW('纳豆', 20, 0, 28),
  ROW('腐竹(干)', 20, 0, 28),
  ROW('马苏里拉', 20, 0, 29),
  ROW('奶油芝士', 20, 0, 29),
  ROW('奶酪片', 20, 0, 29),
  ROW('淡奶', 21, 0, 29),
  ROW('椰浆', 21, 0, 29),
  ROW('椰奶', 21, 0, 29),
  ROW('黑糖', 20, 0, 30),
  ROW('陈醋', 21, 0, 30),
  ROW('香醋', 21, 0, 30),
  ROW('米醋', 21, 0, 30),
  ROW('白醋', 21, 0, 30),
  ROW('黄酒', 21, 0, 30),
  ROW('白酒', 21, 0, 30),
  ROW('芝麻酱', 20, 0, 30),
  ROW('花生酱', 20, 0, 30),
  ROW('辣椒酱', 20, 0, 30),
  ROW('剁椒', 20, 0, 30),
  ROW('豆豉', 20, 0, 30),
  ROW('腐乳', 20, 0, 30),
  ROW('麻椒', 20, 0, 30),
  ROW('香叶', 20, 0, 30),
  ROW('小茴香', 20, 0, 30),
  ROW('孜然', 20, 0, 30),
  ROW('咖喱粉', 20, 0, 30),
  ROW('吉士粉', 20, 0, 30),
  ROW('酵母', 20, 0, 30),
  ROW('蜂蜜', 21, 0, 30),
  ROW('麦芽糖', 20, 0, 30),
  ROW('沙拉酱', 21, 0, 30),
  ROW('蛋黄酱', 21, 0, 30),
  ROW('番茄沙司', 21, 0, 30),
  ROW('甜辣酱', 21, 0, 30),
  ROW('海鲜酱', 20, 0, 30),
  ROW('叉烧酱', 20, 0, 30),
  ROW('沙茶酱', 20, 0, 30),
  ROW('XO酱', 20, 0, 30),
  ROW('辣椒油', 21, 0, 30),
  ROW('花椒油', 21, 0, 30),
  ROW('藤椒油', 21, 0, 30),
  ROW('葱油', 21, 0, 30),
  ROW('虾油', 21, 0, 30),
  ROW('味精', 20, 0, 30),
  ROW('鸡精', 20, 0, 30),
  ROW('红曲米', 20, 0, 30),
  ROW('食用碱', 20, 0, 30),
  ROW('小苏打', 20, 0, 30),
  ROW('泡打粉', 20, 0, 30),
  ROW('葵花籽油', 21, 0, 30),
  ROW('玉米油', 21, 0, 30),
  ROW('大豆油', 21, 0, 30),
  ROW('椰子油', 21, 0, 30),
  ROW('茶油', 21, 0, 30),
  ROW('树莓', 22, 0, 31),
  ROW('油桃', 22, 0, 31),
  ROW('无花果', 22, 0, 31),
  ROW('椰子', 22, 0, 31),
  ROW('牛油果', 22, 0, 31),
  ROW('柠檬', 22, 0, 31),
  ROW('百香果', 22, 0, 31),
  ROW('山楂', 22, 0, 31),
  ROW('枣', 22, 0, 31),
  ROW('柿子', 22, 0, 31),
  ROW('木瓜', 22, 0, 31),
  ROW('甜瓜', 22, 0, 31),
  ROW('糙米', 20, 0, NULL),
  ROW('黑米', 20, 0, NULL),
  ROW('藜麦', 20, 0, NULL),
  ROW('饺子皮', 22, 0, NULL),
  ROW('馄饨皮', 22, 0, NULL),
  ROW('紫薯', 20, 0, NULL),
  ROW('燕麦片', 20, 0, NULL),
  ROW('石花菜', 20, 0, 26),
  ROW('羊栖菜', 20, 0, 26)
) AS v(name, unit_id, price, purchase_category_id)
WHERE NOT EXISTS (SELECT 1 FROM ingredient ex WHERE ex.name = v.name);

-- 2) 每食材 6 项营养（INSERT...SELECT by name）

-- 小米椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 32 FROM ingredient WHERE name='小米椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='小米椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='小米椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7 FROM ingredient WHERE name='小米椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4 FROM ingredient WHERE name='小米椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='小米椒';

-- 杭椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 25 FROM ingredient WHERE name='杭椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='杭椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='杭椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.8 FROM ingredient WHERE name='杭椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3 FROM ingredient WHERE name='杭椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='杭椒';

-- 二荆条  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 27 FROM ingredient WHERE name='二荆条';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='二荆条';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='二荆条';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6 FROM ingredient WHERE name='二荆条';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.2 FROM ingredient WHERE name='二荆条';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='二荆条';

-- 螺丝椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='螺丝椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='螺丝椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='螺丝椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.6 FROM ingredient WHERE name='螺丝椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.9 FROM ingredient WHERE name='螺丝椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='螺丝椒';

-- 线椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 24 FROM ingredient WHERE name='线椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='线椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='线椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='线椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.8 FROM ingredient WHERE name='线椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='线椒';

-- 朝天椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 32 FROM ingredient WHERE name='朝天椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='朝天椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='朝天椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.2 FROM ingredient WHERE name='朝天椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4 FROM ingredient WHERE name='朝天椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='朝天椒';

-- 灯笼椒
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='灯笼椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1 FROM ingredient WHERE name='灯笼椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='灯笼椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.3 FROM ingredient WHERE name='灯笼椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='灯笼椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='灯笼椒';

-- 牛角椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='牛角椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1 FROM ingredient WHERE name='牛角椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='牛角椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.3 FROM ingredient WHERE name='牛角椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='牛角椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='牛角椒';

-- 羊角椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 23 FROM ingredient WHERE name='羊角椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1 FROM ingredient WHERE name='羊角椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='羊角椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='羊角椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='羊角椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='羊角椒';

-- 彩椒(红)
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='彩椒(红)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1 FROM ingredient WHERE name='彩椒(红)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='彩椒(红)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6 FROM ingredient WHERE name='彩椒(红)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.2 FROM ingredient WHERE name='彩椒(红)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='彩椒(红)';

-- 彩椒(黄)  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 27 FROM ingredient WHERE name='彩椒(黄)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1 FROM ingredient WHERE name='彩椒(黄)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='彩椒(黄)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.2 FROM ingredient WHERE name='彩椒(黄)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4 FROM ingredient WHERE name='彩椒(黄)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='彩椒(黄)';

-- 彩椒(橙)  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='彩椒(橙)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1 FROM ingredient WHERE name='彩椒(橙)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='彩椒(橙)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6 FROM ingredient WHERE name='彩椒(橙)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4 FROM ingredient WHERE name='彩椒(橙)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='彩椒(橙)';

-- 青尖椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='青尖椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1 FROM ingredient WHERE name='青尖椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='青尖椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='青尖椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.2 FROM ingredient WHERE name='青尖椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='青尖椒';

-- 红尖椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 32 FROM ingredient WHERE name='红尖椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.3 FROM ingredient WHERE name='红尖椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='红尖椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.4 FROM ingredient WHERE name='红尖椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.2 FROM ingredient WHERE name='红尖椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='红尖椒';

-- 美人椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 25 FROM ingredient WHERE name='美人椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='美人椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='美人椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.8 FROM ingredient WHERE name='美人椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3 FROM ingredient WHERE name='美人椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='美人椒';

-- 黄灯笼椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 28 FROM ingredient WHERE name='黄灯笼椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='黄灯笼椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='黄灯笼椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.5 FROM ingredient WHERE name='黄灯笼椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.5 FROM ingredient WHERE name='黄灯笼椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='黄灯笼椒';

-- 泡椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='泡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.8 FROM ingredient WHERE name='泡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='泡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.5 FROM ingredient WHERE name='泡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2 FROM ingredient WHERE name='泡椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='泡椒';

-- 圣女果
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='圣女果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1 FROM ingredient WHERE name='圣女果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='圣女果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.8 FROM ingredient WHERE name='圣女果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 5 FROM ingredient WHERE name='圣女果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='圣女果';

-- 樱桃番茄  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='樱桃番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1 FROM ingredient WHERE name='樱桃番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='樱桃番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.8 FROM ingredient WHERE name='樱桃番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 5 FROM ingredient WHERE name='樱桃番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='樱桃番茄';

-- 牛番茄  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 18 FROM ingredient WHERE name='牛番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='牛番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='牛番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.9 FROM ingredient WHERE name='牛番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='牛番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='牛番茄';

-- 水果番茄  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 20 FROM ingredient WHERE name='水果番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='水果番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='水果番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.2 FROM ingredient WHERE name='水果番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.8 FROM ingredient WHERE name='水果番茄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='水果番茄';

-- 茄子(长)  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 21 FROM ingredient WHERE name='茄子(长)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='茄子(长)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='茄子(长)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.9 FROM ingredient WHERE name='茄子(长)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='茄子(长)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='茄子(长)';

-- 茄子(圆)  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 23 FROM ingredient WHERE name='茄子(圆)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='茄子(圆)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='茄子(圆)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5 FROM ingredient WHERE name='茄子(圆)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='茄子(圆)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='茄子(圆)';

-- 绿茄子  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 21 FROM ingredient WHERE name='绿茄子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='绿茄子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='绿茄子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.9 FROM ingredient WHERE name='绿茄子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='绿茄子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='绿茄子';

-- 天津白  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 15 FROM ingredient WHERE name='天津白';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='天津白';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='天津白';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.9 FROM ingredient WHERE name='天津白';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.5 FROM ingredient WHERE name='天津白';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 23 FROM ingredient WHERE name='天津白';

-- 快菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 15 FROM ingredient WHERE name='快菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='快菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='快菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.9 FROM ingredient WHERE name='快菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.5 FROM ingredient WHERE name='快菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 23 FROM ingredient WHERE name='快菜';

-- 菜心  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 17 FROM ingredient WHERE name='菜心';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.6 FROM ingredient WHERE name='菜心';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='菜心';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3 FROM ingredient WHERE name='菜心';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='菜心';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 25 FROM ingredient WHERE name='菜心';

-- 奶白菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 13 FROM ingredient WHERE name='奶白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='奶白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='奶白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.6 FROM ingredient WHERE name='奶白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='奶白菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 23 FROM ingredient WHERE name='奶白菜';

-- 红萝卜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='红萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1 FROM ingredient WHERE name='红萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='红萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5 FROM ingredient WHERE name='红萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='红萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 26 FROM ingredient WHERE name='红萝卜';

-- 心里美
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 23 FROM ingredient WHERE name='心里美';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.3 FROM ingredient WHERE name='心里美';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='心里美';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5 FROM ingredient WHERE name='心里美';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='心里美';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 26 FROM ingredient WHERE name='心里美';

-- 水萝卜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 20 FROM ingredient WHERE name='水萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='水萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='水萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.8 FROM ingredient WHERE name='水萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.4 FROM ingredient WHERE name='水萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 26 FROM ingredient WHERE name='水萝卜';

-- 樱桃萝卜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 18 FROM ingredient WHERE name='樱桃萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='樱桃萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='樱桃萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.2 FROM ingredient WHERE name='樱桃萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.2 FROM ingredient WHERE name='樱桃萝卜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 26 FROM ingredient WHERE name='樱桃萝卜';

-- 佛手瓜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 16 FROM ingredient WHERE name='佛手瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='佛手瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='佛手瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.8 FROM ingredient WHERE name='佛手瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.8 FROM ingredient WHERE name='佛手瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='佛手瓜';

-- 蛇瓜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 17 FROM ingredient WHERE name='蛇瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='蛇瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='蛇瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.9 FROM ingredient WHERE name='蛇瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.6 FROM ingredient WHERE name='蛇瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='蛇瓜';

-- 瓠瓜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 15 FROM ingredient WHERE name='瓠瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.8 FROM ingredient WHERE name='瓠瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='瓠瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.5 FROM ingredient WHERE name='瓠瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.5 FROM ingredient WHERE name='瓠瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='瓠瓜';

-- 越瓜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 16 FROM ingredient WHERE name='越瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.8 FROM ingredient WHERE name='越瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='越瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.6 FROM ingredient WHERE name='越瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2 FROM ingredient WHERE name='越瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='越瓜';

-- 香菇
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 19 FROM ingredient WHERE name='香菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.2 FROM ingredient WHERE name='香菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='香菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.2 FROM ingredient WHERE name='香菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='香菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='香菇';

-- 口蘑
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='口蘑';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.3 FROM ingredient WHERE name='口蘑';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='口蘑';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.6 FROM ingredient WHERE name='口蘑';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='口蘑';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='口蘑';

-- 蟹味菇  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 18 FROM ingredient WHERE name='蟹味菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.1 FROM ingredient WHERE name='蟹味菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='蟹味菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4 FROM ingredient WHERE name='蟹味菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='蟹味菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='蟹味菇';

-- 白玉菇  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 18 FROM ingredient WHERE name='白玉菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.1 FROM ingredient WHERE name='白玉菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='白玉菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4 FROM ingredient WHERE name='白玉菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='白玉菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='白玉菇';

-- 松茸  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 27 FROM ingredient WHERE name='松茸';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.2 FROM ingredient WHERE name='松茸';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.5 FROM ingredient WHERE name='松茸';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.2 FROM ingredient WHERE name='松茸';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='松茸';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='松茸';

-- 鸡枞菌  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 24 FROM ingredient WHERE name='鸡枞菌';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3 FROM ingredient WHERE name='鸡枞菌';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='鸡枞菌';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.8 FROM ingredient WHERE name='鸡枞菌';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='鸡枞菌';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='鸡枞菌';

-- 木耳(干)
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 205 FROM ingredient WHERE name='木耳(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 12.1 FROM ingredient WHERE name='木耳(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.5 FROM ingredient WHERE name='木耳(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 65.6 FROM ingredient WHERE name='木耳(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='木耳(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='木耳(干)';

-- 银耳(干)
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 200 FROM ingredient WHERE name='银耳(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 10 FROM ingredient WHERE name='银耳(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.4 FROM ingredient WHERE name='银耳(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 65.6 FROM ingredient WHERE name='银耳(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='银耳(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='银耳(干)';

-- 竹荪  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 155 FROM ingredient WHERE name='竹荪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.8 FROM ingredient WHERE name='竹荪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.1 FROM ingredient WHERE name='竹荪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 60 FROM ingredient WHERE name='竹荪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='竹荪';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='竹荪';

-- 猴头菇  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='猴头菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2 FROM ingredient WHERE name='猴头菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='猴头菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.2 FROM ingredient WHERE name='猴头菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='猴头菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='猴头菇';

-- 虫草花  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 29 FROM ingredient WHERE name='虫草花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.2 FROM ingredient WHERE name='虫草花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='虫草花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6 FROM ingredient WHERE name='虫草花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='虫草花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='虫草花';

-- 茶树菇  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 25 FROM ingredient WHERE name='茶树菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3 FROM ingredient WHERE name='茶树菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='茶树菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6 FROM ingredient WHERE name='茶树菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='茶树菇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='茶树菇';

-- 生菜(圆)  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 13 FROM ingredient WHERE name='生菜(圆)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.3 FROM ingredient WHERE name='生菜(圆)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='生菜(圆)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.1 FROM ingredient WHERE name='生菜(圆)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='生菜(圆)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='生菜(圆)';

-- 生菜(罗曼)  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 16 FROM ingredient WHERE name='生菜(罗曼)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='生菜(罗曼)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='生菜(罗曼)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3 FROM ingredient WHERE name='生菜(罗曼)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='生菜(罗曼)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='生菜(罗曼)';

-- 罗马生菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 16 FROM ingredient WHERE name='罗马生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='罗马生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='罗马生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3 FROM ingredient WHERE name='罗马生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='罗马生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='罗马生菜';

-- 结球生菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 13 FROM ingredient WHERE name='结球生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.3 FROM ingredient WHERE name='结球生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='结球生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.1 FROM ingredient WHERE name='结球生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='结球生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='结球生菜';

-- 奶油生菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 15 FROM ingredient WHERE name='奶油生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='奶油生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='奶油生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.5 FROM ingredient WHERE name='奶油生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='奶油生菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='奶油生菜';

-- 苋菜(红)  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 25 FROM ingredient WHERE name='苋菜(红)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.8 FROM ingredient WHERE name='苋菜(红)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='苋菜(红)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='苋菜(红)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='苋菜(红)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='苋菜(红)';

-- 苋菜(绿)  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 25 FROM ingredient WHERE name='苋菜(绿)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.8 FROM ingredient WHERE name='苋菜(绿)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='苋菜(绿)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='苋菜(绿)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='苋菜(绿)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='苋菜(绿)';

-- 荠菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 27 FROM ingredient WHERE name='荠菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.9 FROM ingredient WHERE name='荠菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='荠菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.7 FROM ingredient WHERE name='荠菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='荠菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='荠菜';

-- 韭黄  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='韭黄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.3 FROM ingredient WHERE name='韭黄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='韭黄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.8 FROM ingredient WHERE name='韭黄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='韭黄';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='韭黄';

-- 蒜薹  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 53 FROM ingredient WHERE name='蒜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.1 FROM ingredient WHERE name='蒜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='蒜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 11.5 FROM ingredient WHERE name='蒜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='蒜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='蒜薹';

-- 蒜苗  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 40 FROM ingredient WHERE name='蒜苗';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.1 FROM ingredient WHERE name='蒜苗';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='蒜苗';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 8 FROM ingredient WHERE name='蒜苗';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='蒜苗';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='蒜苗';

-- 枸杞叶  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 42 FROM ingredient WHERE name='枸杞叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5.6 FROM ingredient WHERE name='枸杞叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.1 FROM ingredient WHERE name='枸杞叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.8 FROM ingredient WHERE name='枸杞叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='枸杞叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='枸杞叶';

-- 红薯叶  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 30 FROM ingredient WHERE name='红薯叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.3 FROM ingredient WHERE name='红薯叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='红薯叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6 FROM ingredient WHERE name='红薯叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='红薯叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='红薯叶';

-- 木耳菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 20 FROM ingredient WHERE name='木耳菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.6 FROM ingredient WHERE name='木耳菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='木耳菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.6 FROM ingredient WHERE name='木耳菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.8 FROM ingredient WHERE name='木耳菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='木耳菜';

-- 紫苏  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 37 FROM ingredient WHERE name='紫苏';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.9 FROM ingredient WHERE name='紫苏';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.3 FROM ingredient WHERE name='紫苏';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='紫苏';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='紫苏';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='紫苏';

-- 薄荷  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 70 FROM ingredient WHERE name='薄荷';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.8 FROM ingredient WHERE name='薄荷';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.9 FROM ingredient WHERE name='薄荷';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 14.9 FROM ingredient WHERE name='薄荷';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='薄荷';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='薄荷';

-- 九层塔  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 23 FROM ingredient WHERE name='九层塔';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.3 FROM ingredient WHERE name='九层塔';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='九层塔';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4 FROM ingredient WHERE name='九层塔';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='九层塔';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='九层塔';

-- 罗勒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 23 FROM ingredient WHERE name='罗勒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.3 FROM ingredient WHERE name='罗勒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='罗勒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4 FROM ingredient WHERE name='罗勒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='罗勒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='罗勒';

-- 迷迭香  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 131 FROM ingredient WHERE name='迷迭香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.3 FROM ingredient WHERE name='迷迭香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 5.9 FROM ingredient WHERE name='迷迭香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 20.7 FROM ingredient WHERE name='迷迭香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='迷迭香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 5 FROM ingredient WHERE name='迷迭香';

-- 百里香  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 101 FROM ingredient WHERE name='百里香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5.6 FROM ingredient WHERE name='百里香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.7 FROM ingredient WHERE name='百里香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 24.5 FROM ingredient WHERE name='百里香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='百里香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 5 FROM ingredient WHERE name='百里香';

-- 香菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 23 FROM ingredient WHERE name='香菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.8 FROM ingredient WHERE name='香菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='香菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.7 FROM ingredient WHERE name='香菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='香菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 5 FROM ingredient WHERE name='香菜';

-- 香椿  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 47 FROM ingredient WHERE name='香椿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.7 FROM ingredient WHERE name='香椿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='香椿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 10.9 FROM ingredient WHERE name='香椿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='香椿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='香椿';

-- 马齿苋  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 25 FROM ingredient WHERE name='马齿苋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.7 FROM ingredient WHERE name='马齿苋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.5 FROM ingredient WHERE name='马齿苋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.3 FROM ingredient WHERE name='马齿苋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='马齿苋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='马齿苋';

-- 蒲公英  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 49 FROM ingredient WHERE name='蒲公英';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.6 FROM ingredient WHERE name='蒲公英';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.7 FROM ingredient WHERE name='蒲公英';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 10.9 FROM ingredient WHERE name='蒲公英';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='蒲公英';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='蒲公英';

-- 鱼腥草  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 31 FROM ingredient WHERE name='鱼腥草';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.1 FROM ingredient WHERE name='鱼腥草';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='鱼腥草';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.2 FROM ingredient WHERE name='鱼腥草';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='鱼腥草';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='鱼腥草';

-- 紫菜薹  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 20 FROM ingredient WHERE name='紫菜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2 FROM ingredient WHERE name='紫菜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='紫菜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.6 FROM ingredient WHERE name='紫菜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='紫菜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='紫菜薹';

-- 红菜薹  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 20 FROM ingredient WHERE name='红菜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2 FROM ingredient WHERE name='红菜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='红菜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.6 FROM ingredient WHERE name='红菜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='红菜薹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='红菜薹';

-- 芥蓝  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 19 FROM ingredient WHERE name='芥蓝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.5 FROM ingredient WHERE name='芥蓝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='芥蓝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.6 FROM ingredient WHERE name='芥蓝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='芥蓝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='芥蓝';

-- 芥菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='芥菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.5 FROM ingredient WHERE name='芥菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='芥菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.6 FROM ingredient WHERE name='芥菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='芥菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='芥菜';

-- 雪里蕻  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 24 FROM ingredient WHERE name='雪里蕻';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.5 FROM ingredient WHERE name='雪里蕻';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='雪里蕻';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4 FROM ingredient WHERE name='雪里蕻';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='雪里蕻';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='雪里蕻';

-- 芝麻菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 25 FROM ingredient WHERE name='芝麻菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.6 FROM ingredient WHERE name='芝麻菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.7 FROM ingredient WHERE name='芝麻菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.7 FROM ingredient WHERE name='芝麻菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='芝麻菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='芝麻菜';

-- 塔菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 15 FROM ingredient WHERE name='塔菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.6 FROM ingredient WHERE name='塔菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='塔菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.6 FROM ingredient WHERE name='塔菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='塔菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='塔菜';

-- 乌塌菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 14 FROM ingredient WHERE name='乌塌菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.6 FROM ingredient WHERE name='乌塌菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='乌塌菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.6 FROM ingredient WHERE name='乌塌菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='乌塌菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='乌塌菜';

-- 秋葵
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 25 FROM ingredient WHERE name='秋葵';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.9 FROM ingredient WHERE name='秋葵';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='秋葵';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.4 FROM ingredient WHERE name='秋葵';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='秋葵';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 25 FROM ingredient WHERE name='秋葵';

-- 芦笋
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 19 FROM ingredient WHERE name='芦笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='芦笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='芦笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.9 FROM ingredient WHERE name='芦笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='芦笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='芦笋';

-- 竹笋
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 23 FROM ingredient WHERE name='竹笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.6 FROM ingredient WHERE name='竹笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='竹笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.6 FROM ingredient WHERE name='竹笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='竹笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='竹笋';

-- 春笋  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 25 FROM ingredient WHERE name='春笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.4 FROM ingredient WHERE name='春笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='春笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.1 FROM ingredient WHERE name='春笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='春笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='春笋';

-- 冬笋  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 40 FROM ingredient WHERE name='冬笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.1 FROM ingredient WHERE name='冬笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='冬笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.5 FROM ingredient WHERE name='冬笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='冬笋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='冬笋';

-- 茭白  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='茭白';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='茭白';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='茭白';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.9 FROM ingredient WHERE name='茭白';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='茭白';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='茭白';

-- 菱角  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 98 FROM ingredient WHERE name='菱角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.5 FROM ingredient WHERE name='菱角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='菱角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 21.4 FROM ingredient WHERE name='菱角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='菱角';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 60 FROM ingredient WHERE name='菱角';

-- 荸荠  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 61 FROM ingredient WHERE name='荸荠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='荸荠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='荸荠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 14.2 FROM ingredient WHERE name='荸荠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='荸荠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 50 FROM ingredient WHERE name='荸荠';

-- 慈姑  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 94 FROM ingredient WHERE name='慈姑';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.6 FROM ingredient WHERE name='慈姑';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='慈姑';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 19.9 FROM ingredient WHERE name='慈姑';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='慈姑';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 50 FROM ingredient WHERE name='慈姑';

-- 魔芋  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 7 FROM ingredient WHERE name='魔芋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.1 FROM ingredient WHERE name='魔芋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='魔芋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.1 FROM ingredient WHERE name='魔芋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='魔芋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 17 FROM ingredient WHERE name='魔芋';

-- 百合  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 162 FROM ingredient WHERE name='百合';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 3.2 FROM ingredient WHERE name='百合';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='百合';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 38.8 FROM ingredient WHERE name='百合';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='百合';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 35 FROM ingredient WHERE name='百合';

-- 莲子  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 344 FROM ingredient WHERE name='莲子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.2 FROM ingredient WHERE name='莲子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2 FROM ingredient WHERE name='莲子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 64.2 FROM ingredient WHERE name='莲子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='莲子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 35 FROM ingredient WHERE name='莲子';

-- 黄花菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 214 FROM ingredient WHERE name='黄花菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 19.4 FROM ingredient WHERE name='黄花菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.6 FROM ingredient WHERE name='黄花菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 60 FROM ingredient WHERE name='黄花菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='黄花菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 35 FROM ingredient WHERE name='黄花菜';

-- 榨菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 29 FROM ingredient WHERE name='榨菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.2 FROM ingredient WHERE name='榨菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='榨菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6 FROM ingredient WHERE name='榨菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='榨菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='榨菜';

-- 大头菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 23 FROM ingredient WHERE name='大头菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='大头菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='大头菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5 FROM ingredient WHERE name='大头菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='大头菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='大头菜';

-- 苤蓝  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 29 FROM ingredient WHERE name='苤蓝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.3 FROM ingredient WHERE name='苤蓝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='苤蓝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7 FROM ingredient WHERE name='苤蓝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='苤蓝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='苤蓝';

-- 甜菜根  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 43 FROM ingredient WHERE name='甜菜根';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.6 FROM ingredient WHERE name='甜菜根';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='甜菜根';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 9.6 FROM ingredient WHERE name='甜菜根';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 6.8 FROM ingredient WHERE name='甜菜根';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 64 FROM ingredient WHERE name='甜菜根';

-- 菊芋  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 58 FROM ingredient WHERE name='菊芋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.8 FROM ingredient WHERE name='菊芋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='菊芋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 13.5 FROM ingredient WHERE name='菊芋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='菊芋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 50 FROM ingredient WHERE name='菊芋';

-- 凉薯  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 55 FROM ingredient WHERE name='凉薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='凉薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='凉薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 13 FROM ingredient WHERE name='凉薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='凉薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 50 FROM ingredient WHERE name='凉薯';

-- 贡菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 25 FROM ingredient WHERE name='贡菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2 FROM ingredient WHERE name='贡菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='贡菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.5 FROM ingredient WHERE name='贡菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='贡菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='贡菜';

-- 香葱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 27 FROM ingredient WHERE name='香葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.6 FROM ingredient WHERE name='香葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='香葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.2 FROM ingredient WHERE name='香葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.3 FROM ingredient WHERE name='香葱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='香葱';

-- 洋葱(紫)
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 40 FROM ingredient WHERE name='洋葱(紫)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='洋葱(紫)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='洋葱(紫)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 9 FROM ingredient WHERE name='洋葱(紫)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.4 FROM ingredient WHERE name='洋葱(紫)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='洋葱(紫)';

-- 洋葱(黄)  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 40 FROM ingredient WHERE name='洋葱(黄)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='洋葱(黄)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='洋葱(黄)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 9 FROM ingredient WHERE name='洋葱(黄)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.4 FROM ingredient WHERE name='洋葱(黄)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='洋葱(黄)';

-- 洋葱(白)  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 40 FROM ingredient WHERE name='洋葱(白)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='洋葱(白)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='洋葱(白)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 9 FROM ingredient WHERE name='洋葱(白)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.4 FROM ingredient WHERE name='洋葱(白)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='洋葱(白)';

-- 蒜瓣  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 126 FROM ingredient WHERE name='蒜瓣';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.5 FROM ingredient WHERE name='蒜瓣';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='蒜瓣';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 27.6 FROM ingredient WHERE name='蒜瓣';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='蒜瓣';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='蒜瓣';

-- 仔姜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 41 FROM ingredient WHERE name='仔姜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.3 FROM ingredient WHERE name='仔姜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='仔姜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.6 FROM ingredient WHERE name='仔姜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.5 FROM ingredient WHERE name='仔姜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 5 FROM ingredient WHERE name='仔姜';

-- 韭菜花  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='韭菜花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.4 FROM ingredient WHERE name='韭菜花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='韭菜花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.6 FROM ingredient WHERE name='韭菜花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='韭菜花';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='韭菜花';

-- 四季豆  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 28 FROM ingredient WHERE name='四季豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2 FROM ingredient WHERE name='四季豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='四季豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.7 FROM ingredient WHERE name='四季豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='四季豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='四季豆';

-- 豌豆荚  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 30 FROM ingredient WHERE name='豌豆荚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.8 FROM ingredient WHERE name='豌豆荚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='豌豆荚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.4 FROM ingredient WHERE name='豌豆荚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='豌豆荚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='豌豆荚';

-- 黄豆芽
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 44 FROM ingredient WHERE name='黄豆芽';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4.5 FROM ingredient WHERE name='黄豆芽';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.6 FROM ingredient WHERE name='黄豆芽';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.5 FROM ingredient WHERE name='黄豆芽';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='黄豆芽';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='黄豆芽';

-- 绿豆芽
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 18 FROM ingredient WHERE name='绿豆芽';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.1 FROM ingredient WHERE name='绿豆芽';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='绿豆芽';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.9 FROM ingredient WHERE name='绿豆芽';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='绿豆芽';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='绿豆芽';

-- 豇豆  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 29 FROM ingredient WHERE name='豇豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.7 FROM ingredient WHERE name='豇豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='豇豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.8 FROM ingredient WHERE name='豇豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='豇豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='豇豆';

-- 蚕豆  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 88 FROM ingredient WHERE name='蚕豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 8.8 FROM ingredient WHERE name='蚕豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='蚕豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 19.7 FROM ingredient WHERE name='蚕豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='蚕豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 40 FROM ingredient WHERE name='蚕豆';

-- 刀豆  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 30 FROM ingredient WHERE name='刀豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.5 FROM ingredient WHERE name='刀豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='刀豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6 FROM ingredient WHERE name='刀豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2.6 FROM ingredient WHERE name='刀豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 28 FROM ingredient WHERE name='刀豆';

-- 猪里脊  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 155 FROM ingredient WHERE name='猪里脊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20.2 FROM ingredient WHERE name='猪里脊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 7.9 FROM ingredient WHERE name='猪里脊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='猪里脊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪里脊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪里脊';

-- 猪舌  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 225 FROM ingredient WHERE name='猪舌';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 16.5 FROM ingredient WHERE name='猪舌';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 16 FROM ingredient WHERE name='猪舌';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.2 FROM ingredient WHERE name='猪舌';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪舌';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪舌';

-- 腊肉  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 692 FROM ingredient WHERE name='腊肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 22 FROM ingredient WHERE name='腊肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 60 FROM ingredient WHERE name='腊肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2 FROM ingredient WHERE name='腊肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='腊肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='腊肉';

-- 火腿  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 330 FROM ingredient WHERE name='火腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 16 FROM ingredient WHERE name='火腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 27.4 FROM ingredient WHERE name='火腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.9 FROM ingredient WHERE name='火腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='火腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='火腿';

-- 香肠  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 508 FROM ingredient WHERE name='香肠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 24.1 FROM ingredient WHERE name='香肠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 40.7 FROM ingredient WHERE name='香肠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 11.2 FROM ingredient WHERE name='香肠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='香肠';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='香肠';

-- 培根  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 541 FROM ingredient WHERE name='培根';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 37 FROM ingredient WHERE name='培根';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 41.8 FROM ingredient WHERE name='培根';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.4 FROM ingredient WHERE name='培根';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='培根';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='培根';

-- 牛里脊  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 107 FROM ingredient WHERE name='牛里脊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 22.2 FROM ingredient WHERE name='牛里脊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.5 FROM ingredient WHERE name='牛里脊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.2 FROM ingredient WHERE name='牛里脊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='牛里脊';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='牛里脊';

-- 牛腱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 124 FROM ingredient WHERE name='牛腱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20 FROM ingredient WHERE name='牛腱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 4.2 FROM ingredient WHERE name='牛腱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.2 FROM ingredient WHERE name='牛腱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='牛腱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='牛腱';

-- 牛排  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 271 FROM ingredient WHERE name='牛排';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17 FROM ingredient WHERE name='牛排';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 23 FROM ingredient WHERE name='牛排';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='牛排';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='牛排';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='牛排';

-- 羊肉  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 203 FROM ingredient WHERE name='羊肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 19 FROM ingredient WHERE name='羊肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 14.1 FROM ingredient WHERE name='羊肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='羊肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='羊肉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='羊肉';

-- 羊腿  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 113 FROM ingredient WHERE name='羊腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20 FROM ingredient WHERE name='羊腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 4 FROM ingredient WHERE name='羊腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='羊腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='羊腿';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='羊腿';

-- 猪肚  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 110 FROM ingredient WHERE name='猪肚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 14.6 FROM ingredient WHERE name='猪肚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 5.1 FROM ingredient WHERE name='猪肚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1 FROM ingredient WHERE name='猪肚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪肚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪肚';

-- 猪血  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 55 FROM ingredient WHERE name='猪血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 12.2 FROM ingredient WHERE name='猪血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='猪血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.9 FROM ingredient WHERE name='猪血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='猪血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='猪血';

-- 鸭血  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 108 FROM ingredient WHERE name='鸭血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 13.6 FROM ingredient WHERE name='鸭血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 4 FROM ingredient WHERE name='鸭血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2 FROM ingredient WHERE name='鸭血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鸭血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸭血';

-- 鸡血  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 49 FROM ingredient WHERE name='鸡血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.8 FROM ingredient WHERE name='鸡血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='鸡血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.1 FROM ingredient WHERE name='鸡血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鸡血';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸡血';

-- 牛百叶  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 72 FROM ingredient WHERE name='牛百叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 13 FROM ingredient WHERE name='牛百叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.3 FROM ingredient WHERE name='牛百叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.6 FROM ingredient WHERE name='牛百叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='牛百叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='牛百叶';

-- 毛肚  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 84 FROM ingredient WHERE name='毛肚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 14 FROM ingredient WHERE name='毛肚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2 FROM ingredient WHERE name='毛肚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2 FROM ingredient WHERE name='毛肚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='毛肚';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='毛肚';

-- 黄喉  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 110 FROM ingredient WHERE name='黄喉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18 FROM ingredient WHERE name='黄喉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.5 FROM ingredient WHERE name='黄喉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='黄喉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='黄喉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='黄喉';

-- 鳙鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 100 FROM ingredient WHERE name='鳙鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 15.3 FROM ingredient WHERE name='鳙鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.2 FROM ingredient WHERE name='鳙鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.7 FROM ingredient WHERE name='鳙鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鳙鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鳙鱼';

-- 鲢鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 102 FROM ingredient WHERE name='鲢鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.8 FROM ingredient WHERE name='鲢鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.6 FROM ingredient WHERE name='鲢鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='鲢鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鲢鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鲢鱼';

-- 金枪鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 144 FROM ingredient WHERE name='金枪鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 23.3 FROM ingredient WHERE name='金枪鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 4.9 FROM ingredient WHERE name='金枪鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='金枪鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='金枪鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='金枪鱼';

-- 沙丁鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 89 FROM ingredient WHERE name='沙丁鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 19.8 FROM ingredient WHERE name='沙丁鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.1 FROM ingredient WHERE name='沙丁鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='沙丁鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='沙丁鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='沙丁鱼';

-- 鳗鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 184 FROM ingredient WHERE name='鳗鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.6 FROM ingredient WHERE name='鳗鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 10.8 FROM ingredient WHERE name='鳗鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.3 FROM ingredient WHERE name='鳗鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鳗鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鳗鱼';

-- 鲶鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 105 FROM ingredient WHERE name='鲶鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.3 FROM ingredient WHERE name='鲶鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.6 FROM ingredient WHERE name='鲶鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='鲶鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鲶鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鲶鱼';

-- 黑鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 86 FROM ingredient WHERE name='黑鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.1 FROM ingredient WHERE name='黑鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.4 FROM ingredient WHERE name='黑鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='黑鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='黑鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='黑鱼';

-- 桂鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 117 FROM ingredient WHERE name='桂鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 19.9 FROM ingredient WHERE name='桂鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 4.2 FROM ingredient WHERE name='桂鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='桂鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='桂鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='桂鱼';

-- 马鲛鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 135 FROM ingredient WHERE name='马鲛鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 21.4 FROM ingredient WHERE name='马鲛鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 5.4 FROM ingredient WHERE name='马鲛鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='马鲛鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='马鲛鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='马鲛鱼';

-- 鲅鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 121 FROM ingredient WHERE name='鲅鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 21.4 FROM ingredient WHERE name='鲅鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 3.4 FROM ingredient WHERE name='鲅鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2 FROM ingredient WHERE name='鲅鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鲅鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鲅鱼';

-- 秋刀鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 241 FROM ingredient WHERE name='秋刀鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 20.6 FROM ingredient WHERE name='秋刀鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 17 FROM ingredient WHERE name='秋刀鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.4 FROM ingredient WHERE name='秋刀鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='秋刀鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='秋刀鱼';

-- 多春鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 170 FROM ingredient WHERE name='多春鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18 FROM ingredient WHERE name='多春鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 10 FROM ingredient WHERE name='多春鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1 FROM ingredient WHERE name='多春鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='多春鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='多春鱼';

-- 银鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 105 FROM ingredient WHERE name='银鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.2 FROM ingredient WHERE name='银鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.6 FROM ingredient WHERE name='银鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.6 FROM ingredient WHERE name='银鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='银鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='银鱼';

-- 罗非鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 98 FROM ingredient WHERE name='罗非鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.4 FROM ingredient WHERE name='罗非鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.5 FROM ingredient WHERE name='罗非鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.8 FROM ingredient WHERE name='罗非鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='罗非鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='罗非鱼';

-- 鳊鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 135 FROM ingredient WHERE name='鳊鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.3 FROM ingredient WHERE name='鳊鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 6.3 FROM ingredient WHERE name='鳊鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.2 FROM ingredient WHERE name='鳊鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鳊鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鳊鱼';

-- 泥鳅  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 96 FROM ingredient WHERE name='泥鳅';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.9 FROM ingredient WHERE name='泥鳅';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2 FROM ingredient WHERE name='泥鳅';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.5 FROM ingredient WHERE name='泥鳅';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='泥鳅';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='泥鳅';

-- 鳝鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 89 FROM ingredient WHERE name='鳝鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18 FROM ingredient WHERE name='鳝鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.4 FROM ingredient WHERE name='鳝鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.2 FROM ingredient WHERE name='鳝鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鳝鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鳝鱼';

-- 皮皮虾  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 81 FROM ingredient WHERE name='皮皮虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 11.6 FROM ingredient WHERE name='皮皮虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.7 FROM ingredient WHERE name='皮皮虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 8.3 FROM ingredient WHERE name='皮皮虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='皮皮虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='皮皮虾';

-- 大闸蟹  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 103 FROM ingredient WHERE name='大闸蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.5 FROM ingredient WHERE name='大闸蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.6 FROM ingredient WHERE name='大闸蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.4 FROM ingredient WHERE name='大闸蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='大闸蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='大闸蟹';

-- 梭子蟹  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 95 FROM ingredient WHERE name='梭子蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 13.8 FROM ingredient WHERE name='梭子蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.3 FROM ingredient WHERE name='梭子蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.7 FROM ingredient WHERE name='梭子蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='梭子蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='梭子蟹';

-- 青蟹  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 103 FROM ingredient WHERE name='青蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.5 FROM ingredient WHERE name='青蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.6 FROM ingredient WHERE name='青蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.4 FROM ingredient WHERE name='青蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='青蟹';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='青蟹';

-- 龙虾  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 92 FROM ingredient WHERE name='龙虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.9 FROM ingredient WHERE name='龙虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.1 FROM ingredient WHERE name='龙虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1 FROM ingredient WHERE name='龙虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='龙虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='龙虾';

-- 北极虾  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 101 FROM ingredient WHERE name='北极虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 18.2 FROM ingredient WHERE name='北极虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.4 FROM ingredient WHERE name='北极虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.9 FROM ingredient WHERE name='北极虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='北极虾';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='北极虾';

-- 虾米  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 198 FROM ingredient WHERE name='虾米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 43.7 FROM ingredient WHERE name='虾米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.6 FROM ingredient WHERE name='虾米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='虾米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='虾米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='虾米';

-- 虾皮  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 153 FROM ingredient WHERE name='虾皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 30.7 FROM ingredient WHERE name='虾皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.2 FROM ingredient WHERE name='虾皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.5 FROM ingredient WHERE name='虾皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='虾皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='虾皮';

-- 干贝  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 264 FROM ingredient WHERE name='干贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 55.6 FROM ingredient WHERE name='干贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.4 FROM ingredient WHERE name='干贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.1 FROM ingredient WHERE name='干贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='干贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='干贝';

-- 瑶柱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 264 FROM ingredient WHERE name='瑶柱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 55.6 FROM ingredient WHERE name='瑶柱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.4 FROM ingredient WHERE name='瑶柱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.1 FROM ingredient WHERE name='瑶柱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='瑶柱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='瑶柱';

-- 鲍鱼  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 84 FROM ingredient WHERE name='鲍鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 12.6 FROM ingredient WHERE name='鲍鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.8 FROM ingredient WHERE name='鲍鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.6 FROM ingredient WHERE name='鲍鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='鲍鱼';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鲍鱼';

-- 海参  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 71 FROM ingredient WHERE name='海参';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 16.5 FROM ingredient WHERE name='海参';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='海参';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.5 FROM ingredient WHERE name='海参';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='海参';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='海参';

-- 花甲  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 45 FROM ingredient WHERE name='花甲';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5.5 FROM ingredient WHERE name='花甲';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='花甲';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6 FROM ingredient WHERE name='花甲';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='花甲';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='花甲';

-- 蚬子  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 47 FROM ingredient WHERE name='蚬子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7 FROM ingredient WHERE name='蚬子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='蚬子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5 FROM ingredient WHERE name='蚬子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='蚬子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='蚬子';

-- 蛏子王  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 51 FROM ingredient WHERE name='蛏子王';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.3 FROM ingredient WHERE name='蛏子王';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.5 FROM ingredient WHERE name='蛏子王';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6 FROM ingredient WHERE name='蛏子王';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='蛏子王';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='蛏子王';

-- 青口贝  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 70 FROM ingredient WHERE name='青口贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 11.4 FROM ingredient WHERE name='青口贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.6 FROM ingredient WHERE name='青口贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5 FROM ingredient WHERE name='青口贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='青口贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='青口贝';

-- 文蛤  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 50 FROM ingredient WHERE name='文蛤';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 8 FROM ingredient WHERE name='文蛤';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='文蛤';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5 FROM ingredient WHERE name='文蛤';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='文蛤';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='文蛤';

-- 花蛤  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 45 FROM ingredient WHERE name='花蛤';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5.5 FROM ingredient WHERE name='花蛤';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='花蛤';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6 FROM ingredient WHERE name='花蛤';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='花蛤';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='花蛤';

-- 北极贝  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 85 FROM ingredient WHERE name='北极贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 13 FROM ingredient WHERE name='北极贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.8 FROM ingredient WHERE name='北极贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6 FROM ingredient WHERE name='北极贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='北极贝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='北极贝';

-- 海螺  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 137 FROM ingredient WHERE name='海螺';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 24 FROM ingredient WHERE name='海螺';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.5 FROM ingredient WHERE name='海螺';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 8 FROM ingredient WHERE name='海螺';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='海螺';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='海螺';

-- 田螺  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 60 FROM ingredient WHERE name='田螺';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 11 FROM ingredient WHERE name='田螺';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='田螺';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.6 FROM ingredient WHERE name='田螺';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='田螺';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='田螺';

-- 海带(干)  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 228 FROM ingredient WHERE name='海带(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5.4 FROM ingredient WHERE name='海带(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='海带(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 60 FROM ingredient WHERE name='海带(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='海带(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 17 FROM ingredient WHERE name='海带(干)';

-- 裙带菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 33 FROM ingredient WHERE name='裙带菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.8 FROM ingredient WHERE name='裙带菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='裙带菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7 FROM ingredient WHERE name='裙带菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='裙带菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 17 FROM ingredient WHERE name='裙带菜';

-- 海蜇  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 33 FROM ingredient WHERE name='海蜇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5 FROM ingredient WHERE name='海蜇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='海蜇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.9 FROM ingredient WHERE name='海蜇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='海蜇';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='海蜇';

-- 海蜇皮  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 33 FROM ingredient WHERE name='海蜇皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 5 FROM ingredient WHERE name='海蜇皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='海蜇皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3.9 FROM ingredient WHERE name='海蜇皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='海蜇皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='海蜇皮';

-- 鸽子蛋  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 102 FROM ingredient WHERE name='鸽子蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 9.5 FROM ingredient WHERE name='鸽子蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 6.4 FROM ingredient WHERE name='鸽子蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1.4 FROM ingredient WHERE name='鸽子蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='鸽子蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='鸽子蛋';

-- 鹅蛋  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 196 FROM ingredient WHERE name='鹅蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 11.1 FROM ingredient WHERE name='鹅蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 15.6 FROM ingredient WHERE name='鹅蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.8 FROM ingredient WHERE name='鹅蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.4 FROM ingredient WHERE name='鹅蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='鹅蛋';

-- 茶叶蛋  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 148 FROM ingredient WHERE name='茶叶蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 13 FROM ingredient WHERE name='茶叶蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 9 FROM ingredient WHERE name='茶叶蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.8 FROM ingredient WHERE name='茶叶蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.5 FROM ingredient WHERE name='茶叶蛋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='茶叶蛋';

-- 豆腐丝  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 203 FROM ingredient WHERE name='豆腐丝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 21.5 FROM ingredient WHERE name='豆腐丝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 10.7 FROM ingredient WHERE name='豆腐丝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.2 FROM ingredient WHERE name='豆腐丝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='豆腐丝';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='豆腐丝';

-- 千张  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 260 FROM ingredient WHERE name='千张';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 24.5 FROM ingredient WHERE name='千张';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 16 FROM ingredient WHERE name='千张';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.5 FROM ingredient WHERE name='千张';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='千张';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='千张';

-- 油豆腐  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 244 FROM ingredient WHERE name='油豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17 FROM ingredient WHERE name='油豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 18 FROM ingredient WHERE name='油豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.3 FROM ingredient WHERE name='油豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='油豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='油豆腐';

-- 毛豆腐  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 100 FROM ingredient WHERE name='毛豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 9 FROM ingredient WHERE name='毛豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 5 FROM ingredient WHERE name='毛豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5 FROM ingredient WHERE name='毛豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='毛豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='毛豆腐';

-- 臭豆腐  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 130 FROM ingredient WHERE name='臭豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 11 FROM ingredient WHERE name='臭豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 8 FROM ingredient WHERE name='臭豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4 FROM ingredient WHERE name='臭豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='臭豆腐';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='臭豆腐';

-- 豆筋  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 390 FROM ingredient WHERE name='豆筋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 35 FROM ingredient WHERE name='豆筋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 20 FROM ingredient WHERE name='豆筋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 25 FROM ingredient WHERE name='豆筋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='豆筋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='豆筋';

-- 纳豆  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 190 FROM ingredient WHERE name='纳豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.7 FROM ingredient WHERE name='纳豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 11 FROM ingredient WHERE name='纳豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5 FROM ingredient WHERE name='纳豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='纳豆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 33 FROM ingredient WHERE name='纳豆';

-- 腐竹(干)  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 459 FROM ingredient WHERE name='腐竹(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 44.6 FROM ingredient WHERE name='腐竹(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 21.7 FROM ingredient WHERE name='腐竹(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 22.3 FROM ingredient WHERE name='腐竹(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='腐竹(干)';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='腐竹(干)';

-- 马苏里拉  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 318 FROM ingredient WHERE name='马苏里拉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 21.6 FROM ingredient WHERE name='马苏里拉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 24 FROM ingredient WHERE name='马苏里拉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.2 FROM ingredient WHERE name='马苏里拉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='马苏里拉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='马苏里拉';

-- 奶油芝士  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 350 FROM ingredient WHERE name='奶油芝士';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 6.2 FROM ingredient WHERE name='奶油芝士';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 34.9 FROM ingredient WHERE name='奶油芝士';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.1 FROM ingredient WHERE name='奶油芝士';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.2 FROM ingredient WHERE name='奶油芝士';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='奶油芝士';

-- 奶酪片  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 375 FROM ingredient WHERE name='奶酪片';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 23 FROM ingredient WHERE name='奶酪片';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 30 FROM ingredient WHERE name='奶酪片';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2 FROM ingredient WHERE name='奶酪片';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='奶酪片';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='奶酪片';

-- 淡奶  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 134 FROM ingredient WHERE name='淡奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 6.8 FROM ingredient WHERE name='淡奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 7.5 FROM ingredient WHERE name='淡奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 9.7 FROM ingredient WHERE name='淡奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 9.7 FROM ingredient WHERE name='淡奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='淡奶';

-- 椰浆  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 230 FROM ingredient WHERE name='椰浆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.3 FROM ingredient WHERE name='椰浆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 23.9 FROM ingredient WHERE name='椰浆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5.5 FROM ingredient WHERE name='椰浆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3 FROM ingredient WHERE name='椰浆';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='椰浆';

-- 椰奶  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 93 FROM ingredient WHERE name='椰奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1 FROM ingredient WHERE name='椰奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 9.4 FROM ingredient WHERE name='椰奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 3 FROM ingredient WHERE name='椰奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 2 FROM ingredient WHERE name='椰奶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='椰奶';

-- 黑糖  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 379 FROM ingredient WHERE name='黑糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.7 FROM ingredient WHERE name='黑糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='黑糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 96.6 FROM ingredient WHERE name='黑糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 96 FROM ingredient WHERE name='黑糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='黑糖';

-- 陈醋  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 18 FROM ingredient WHERE name='陈醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.1 FROM ingredient WHERE name='陈醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='陈醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.5 FROM ingredient WHERE name='陈醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='陈醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 5 FROM ingredient WHERE name='陈醋';

-- 香醋  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 16 FROM ingredient WHERE name='香醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2 FROM ingredient WHERE name='香醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='香醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4 FROM ingredient WHERE name='香醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='香醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 5 FROM ingredient WHERE name='香醋';

-- 米醋  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 18 FROM ingredient WHERE name='米醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.1 FROM ingredient WHERE name='米醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='米醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 4.5 FROM ingredient WHERE name='米醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='米醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 5 FROM ingredient WHERE name='米醋';

-- 白醋  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 22 FROM ingredient WHERE name='白醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.5 FROM ingredient WHERE name='白醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='白醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5 FROM ingredient WHERE name='白醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='白醋';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 5 FROM ingredient WHERE name='白醋';

-- 黄酒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 66 FROM ingredient WHERE name='黄酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.2 FROM ingredient WHERE name='黄酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='黄酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 15 FROM ingredient WHERE name='黄酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.2 FROM ingredient WHERE name='黄酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='黄酒';

-- 白酒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 298 FROM ingredient WHERE name='白酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.5 FROM ingredient WHERE name='白酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='白酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='白酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='白酒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='白酒';

-- 芝麻酱
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 631 FROM ingredient WHERE name='芝麻酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 19.2 FROM ingredient WHERE name='芝麻酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 52.7 FROM ingredient WHERE name='芝麻酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 22.2 FROM ingredient WHERE name='芝麻酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='芝麻酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='芝麻酱';

-- 花生酱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 588 FROM ingredient WHERE name='花生酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 25.1 FROM ingredient WHERE name='花生酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 50.4 FROM ingredient WHERE name='花生酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 20 FROM ingredient WHERE name='花生酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 9 FROM ingredient WHERE name='花生酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='花生酱';

-- 辣椒酱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 125 FROM ingredient WHERE name='辣椒酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.5 FROM ingredient WHERE name='辣椒酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 6 FROM ingredient WHERE name='辣椒酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 15 FROM ingredient WHERE name='辣椒酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 5 FROM ingredient WHERE name='辣椒酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='辣椒酱';

-- 剁椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 32 FROM ingredient WHERE name='剁椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='剁椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.4 FROM ingredient WHERE name='剁椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6 FROM ingredient WHERE name='剁椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='剁椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='剁椒';

-- 豆豉  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 259 FROM ingredient WHERE name='豆豉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 24.1 FROM ingredient WHERE name='豆豉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 11 FROM ingredient WHERE name='豆豉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 22 FROM ingredient WHERE name='豆豉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='豆豉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='豆豉';

-- 腐乳  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 135 FROM ingredient WHERE name='腐乳';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 11.4 FROM ingredient WHERE name='腐乳';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 6.6 FROM ingredient WHERE name='腐乳';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 5 FROM ingredient WHERE name='腐乳';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='腐乳';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='腐乳';

-- 麻椒  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 258 FROM ingredient WHERE name='麻椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 6.7 FROM ingredient WHERE name='麻椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 8.9 FROM ingredient WHERE name='麻椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 56 FROM ingredient WHERE name='麻椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='麻椒';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 5 FROM ingredient WHERE name='麻椒';

-- 香叶  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 313 FROM ingredient WHERE name='香叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.6 FROM ingredient WHERE name='香叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 8.4 FROM ingredient WHERE name='香叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 75 FROM ingredient WHERE name='香叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='香叶';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 5 FROM ingredient WHERE name='香叶';

-- 小茴香  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 345 FROM ingredient WHERE name='小茴香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 14 FROM ingredient WHERE name='小茴香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 15 FROM ingredient WHERE name='小茴香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 52 FROM ingredient WHERE name='小茴香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='小茴香';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 5 FROM ingredient WHERE name='小茴香';

-- 孜然  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 375 FROM ingredient WHERE name='孜然';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 17.8 FROM ingredient WHERE name='孜然';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 22.3 FROM ingredient WHERE name='孜然';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 44 FROM ingredient WHERE name='孜然';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='孜然';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 5 FROM ingredient WHERE name='孜然';

-- 咖喱粉  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 325 FROM ingredient WHERE name='咖喱粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 14 FROM ingredient WHERE name='咖喱粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 14 FROM ingredient WHERE name='咖喱粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 56 FROM ingredient WHERE name='咖喱粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='咖喱粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 5 FROM ingredient WHERE name='咖喱粉';

-- 吉士粉  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 380 FROM ingredient WHERE name='吉士粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 8 FROM ingredient WHERE name='吉士粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 5 FROM ingredient WHERE name='吉士粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 75 FROM ingredient WHERE name='吉士粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 30 FROM ingredient WHERE name='吉士粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='吉士粉';

-- 酵母  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 105 FROM ingredient WHERE name='酵母';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 12 FROM ingredient WHERE name='酵母';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1 FROM ingredient WHERE name='酵母';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 18 FROM ingredient WHERE name='酵母';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='酵母';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='酵母';

-- 蜂蜜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 321 FROM ingredient WHERE name='蜂蜜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.4 FROM ingredient WHERE name='蜂蜜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.9 FROM ingredient WHERE name='蜂蜜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 75.6 FROM ingredient WHERE name='蜂蜜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 75 FROM ingredient WHERE name='蜂蜜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 55 FROM ingredient WHERE name='蜂蜜';

-- 麦芽糖  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 336 FROM ingredient WHERE name='麦芽糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='麦芽糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='麦芽糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 82 FROM ingredient WHERE name='麦芽糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 82 FROM ingredient WHERE name='麦芽糖';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 105 FROM ingredient WHERE name='麦芽糖';

-- 沙拉酱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 680 FROM ingredient WHERE name='沙拉酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='沙拉酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 70 FROM ingredient WHERE name='沙拉酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 2.4 FROM ingredient WHERE name='沙拉酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='沙拉酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='沙拉酱';

-- 蛋黄酱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 680 FROM ingredient WHERE name='蛋黄酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='蛋黄酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 75 FROM ingredient WHERE name='蛋黄酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 1 FROM ingredient WHERE name='蛋黄酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='蛋黄酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='蛋黄酱';

-- 番茄沙司  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 107 FROM ingredient WHERE name='番茄沙司';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1 FROM ingredient WHERE name='番茄沙司';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='番茄沙司';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 25 FROM ingredient WHERE name='番茄沙司';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 20 FROM ingredient WHERE name='番茄沙司';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 35 FROM ingredient WHERE name='番茄沙司';

-- 甜辣酱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 150 FROM ingredient WHERE name='甜辣酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1 FROM ingredient WHERE name='甜辣酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1 FROM ingredient WHERE name='甜辣酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 33 FROM ingredient WHERE name='甜辣酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 28 FROM ingredient WHERE name='甜辣酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 35 FROM ingredient WHERE name='甜辣酱';

-- 海鲜酱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 200 FROM ingredient WHERE name='海鲜酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4 FROM ingredient WHERE name='海鲜酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1 FROM ingredient WHERE name='海鲜酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 44 FROM ingredient WHERE name='海鲜酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 30 FROM ingredient WHERE name='海鲜酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 35 FROM ingredient WHERE name='海鲜酱';

-- 叉烧酱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 220 FROM ingredient WHERE name='叉烧酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4 FROM ingredient WHERE name='叉烧酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2 FROM ingredient WHERE name='叉烧酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 45 FROM ingredient WHERE name='叉烧酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 35 FROM ingredient WHERE name='叉烧酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 35 FROM ingredient WHERE name='叉烧酱';

-- 沙茶酱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 245 FROM ingredient WHERE name='沙茶酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 8 FROM ingredient WHERE name='沙茶酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 13 FROM ingredient WHERE name='沙茶酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 25 FROM ingredient WHERE name='沙茶酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 12 FROM ingredient WHERE name='沙茶酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 35 FROM ingredient WHERE name='沙茶酱';

-- XO酱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 270 FROM ingredient WHERE name='XO酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 8 FROM ingredient WHERE name='XO酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 18 FROM ingredient WHERE name='XO酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 18 FROM ingredient WHERE name='XO酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 5 FROM ingredient WHERE name='XO酱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='XO酱';

-- 辣椒油  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 900 FROM ingredient WHERE name='辣椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='辣椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='辣椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='辣椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='辣椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='辣椒油';

-- 花椒油  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 898 FROM ingredient WHERE name='花椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='花椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='花椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='花椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='花椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='花椒油';

-- 藤椒油  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 898 FROM ingredient WHERE name='藤椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='藤椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='藤椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='藤椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='藤椒油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='藤椒油';

-- 葱油  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 899 FROM ingredient WHERE name='葱油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='葱油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='葱油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='葱油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='葱油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='葱油';

-- 虾油  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 898 FROM ingredient WHERE name='虾油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.5 FROM ingredient WHERE name='虾油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99 FROM ingredient WHERE name='虾油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='虾油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='虾油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='虾油';

-- 味精  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 268 FROM ingredient WHERE name='味精';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 40.1 FROM ingredient WHERE name='味精';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='味精';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 26.5 FROM ingredient WHERE name='味精';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='味精';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='味精';

-- 鸡精  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 195 FROM ingredient WHERE name='鸡精';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 10.7 FROM ingredient WHERE name='鸡精';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.8 FROM ingredient WHERE name='鸡精';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 38 FROM ingredient WHERE name='鸡精';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 5 FROM ingredient WHERE name='鸡精';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='鸡精';

-- 红曲米  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 345 FROM ingredient WHERE name='红曲米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7 FROM ingredient WHERE name='红曲米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1 FROM ingredient WHERE name='红曲米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 75 FROM ingredient WHERE name='红曲米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='红曲米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='红曲米';

-- 食用碱  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 0 FROM ingredient WHERE name='食用碱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='食用碱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='食用碱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='食用碱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='食用碱';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='食用碱';

-- 小苏打  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 0 FROM ingredient WHERE name='小苏打';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='小苏打';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='小苏打';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='小苏打';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='小苏打';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='小苏打';

-- 泡打粉  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 105 FROM ingredient WHERE name='泡打粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='泡打粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0 FROM ingredient WHERE name='泡打粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 25 FROM ingredient WHERE name='泡打粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='泡打粉';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='泡打粉';

-- 葵花籽油  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 899 FROM ingredient WHERE name='葵花籽油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='葵花籽油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='葵花籽油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='葵花籽油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='葵花籽油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='葵花籽油';

-- 玉米油  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 895 FROM ingredient WHERE name='玉米油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='玉米油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.2 FROM ingredient WHERE name='玉米油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0.5 FROM ingredient WHERE name='玉米油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='玉米油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='玉米油';

-- 大豆油  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 899 FROM ingredient WHERE name='大豆油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='大豆油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='大豆油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='大豆油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='大豆油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='大豆油';

-- 椰子油  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 892 FROM ingredient WHERE name='椰子油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='椰子油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.1 FROM ingredient WHERE name='椰子油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='椰子油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='椰子油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='椰子油';

-- 茶油  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 899 FROM ingredient WHERE name='茶油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0 FROM ingredient WHERE name='茶油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 99.9 FROM ingredient WHERE name='茶油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 0 FROM ingredient WHERE name='茶油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='茶油';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 0 FROM ingredient WHERE name='茶油';

-- 树莓  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 52 FROM ingredient WHERE name='树莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.2 FROM ingredient WHERE name='树莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.7 FROM ingredient WHERE name='树莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 11.9 FROM ingredient WHERE name='树莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 4.4 FROM ingredient WHERE name='树莓';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 26 FROM ingredient WHERE name='树莓';

-- 油桃  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 44 FROM ingredient WHERE name='油桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='油桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='油桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 10 FROM ingredient WHERE name='油桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 7.4 FROM ingredient WHERE name='油桃';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 42 FROM ingredient WHERE name='油桃';

-- 无花果  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 65 FROM ingredient WHERE name='无花果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='无花果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='无花果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 16 FROM ingredient WHERE name='无花果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 12 FROM ingredient WHERE name='无花果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 61 FROM ingredient WHERE name='无花果';

-- 椰子  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 241 FROM ingredient WHERE name='椰子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 4 FROM ingredient WHERE name='椰子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 12.1 FROM ingredient WHERE name='椰子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 31.3 FROM ingredient WHERE name='椰子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 6.2 FROM ingredient WHERE name='椰子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 40 FROM ingredient WHERE name='椰子';

-- 牛油果
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 171 FROM ingredient WHERE name='牛油果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2 FROM ingredient WHERE name='牛油果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 15.3 FROM ingredient WHERE name='牛油果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7.4 FROM ingredient WHERE name='牛油果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.7 FROM ingredient WHERE name='牛油果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 15 FROM ingredient WHERE name='牛油果';

-- 柠檬  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 37 FROM ingredient WHERE name='柠檬';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='柠檬';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1.2 FROM ingredient WHERE name='柠檬';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6.2 FROM ingredient WHERE name='柠檬';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='柠檬';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 34 FROM ingredient WHERE name='柠檬';

-- 百香果  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 97 FROM ingredient WHERE name='百香果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 2.2 FROM ingredient WHERE name='百香果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.7 FROM ingredient WHERE name='百香果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 23.4 FROM ingredient WHERE name='百香果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 11.2 FROM ingredient WHERE name='百香果';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 30 FROM ingredient WHERE name='百香果';

-- 山楂  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 102 FROM ingredient WHERE name='山楂';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.5 FROM ingredient WHERE name='山楂';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.6 FROM ingredient WHERE name='山楂';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 25 FROM ingredient WHERE name='山楂';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 22 FROM ingredient WHERE name='山楂';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 60 FROM ingredient WHERE name='山楂';

-- 枣  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 122 FROM ingredient WHERE name='枣';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.1 FROM ingredient WHERE name='枣';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.3 FROM ingredient WHERE name='枣';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 30.5 FROM ingredient WHERE name='枣';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 28.6 FROM ingredient WHERE name='枣';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 50 FROM ingredient WHERE name='枣';

-- 柿子  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 74 FROM ingredient WHERE name='柿子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.4 FROM ingredient WHERE name='柿子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='柿子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 18.5 FROM ingredient WHERE name='柿子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 12.5 FROM ingredient WHERE name='柿子';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 50 FROM ingredient WHERE name='柿子';

-- 木瓜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 29 FROM ingredient WHERE name='木瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.4 FROM ingredient WHERE name='木瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='木瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 7 FROM ingredient WHERE name='木瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 5 FROM ingredient WHERE name='木瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 25 FROM ingredient WHERE name='木瓜';

-- 甜瓜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 36 FROM ingredient WHERE name='甜瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 0.9 FROM ingredient WHERE name='甜瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='甜瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 8 FROM ingredient WHERE name='甜瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 6.2 FROM ingredient WHERE name='甜瓜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 65 FROM ingredient WHERE name='甜瓜';

-- 糙米  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 348 FROM ingredient WHERE name='糙米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 7.4 FROM ingredient WHERE name='糙米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.7 FROM ingredient WHERE name='糙米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 77.2 FROM ingredient WHERE name='糙米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.7 FROM ingredient WHERE name='糙米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 70 FROM ingredient WHERE name='糙米';

-- 黑米  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 341 FROM ingredient WHERE name='黑米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 9.4 FROM ingredient WHERE name='黑米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 2.5 FROM ingredient WHERE name='黑米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 72.2 FROM ingredient WHERE name='黑米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0.7 FROM ingredient WHERE name='黑米';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 55 FROM ingredient WHERE name='黑米';

-- 藜麦  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 368 FROM ingredient WHERE name='藜麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 14.1 FROM ingredient WHERE name='藜麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 6.1 FROM ingredient WHERE name='藜麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 64.2 FROM ingredient WHERE name='藜麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='藜麦';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 53 FROM ingredient WHERE name='藜麦';

-- 饺子皮  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 265 FROM ingredient WHERE name='饺子皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 8 FROM ingredient WHERE name='饺子皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1 FROM ingredient WHERE name='饺子皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 55 FROM ingredient WHERE name='饺子皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='饺子皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 80 FROM ingredient WHERE name='饺子皮';

-- 馄饨皮  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 265 FROM ingredient WHERE name='馄饨皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 8 FROM ingredient WHERE name='馄饨皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 1 FROM ingredient WHERE name='馄饨皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 55 FROM ingredient WHERE name='馄饨皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1 FROM ingredient WHERE name='馄饨皮';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 80 FROM ingredient WHERE name='馄饨皮';

-- 紫薯  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 82 FROM ingredient WHERE name='紫薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.4 FROM ingredient WHERE name='紫薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.2 FROM ingredient WHERE name='紫薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 18.7 FROM ingredient WHERE name='紫薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 3.2 FROM ingredient WHERE name='紫薯';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 77 FROM ingredient WHERE name='紫薯';

-- 燕麦片  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 367 FROM ingredient WHERE name='燕麦片';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 15 FROM ingredient WHERE name='燕麦片';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 6.7 FROM ingredient WHERE name='燕麦片';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 61.6 FROM ingredient WHERE name='燕麦片';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 1.2 FROM ingredient WHERE name='燕麦片';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 55 FROM ingredient WHERE name='燕麦片';

-- 石花菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 26 FROM ingredient WHERE name='石花菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 1.5 FROM ingredient WHERE name='石花菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.1 FROM ingredient WHERE name='石花菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 6 FROM ingredient WHERE name='石花菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='石花菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 17 FROM ingredient WHERE name='石花菜';

-- 羊栖菜  -- 【估】参考估值
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 1, 152 FROM ingredient WHERE name='羊栖菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 2, 9.2 FROM ingredient WHERE name='羊栖菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 3, 0.8 FROM ingredient WHERE name='羊栖菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 4, 35 FROM ingredient WHERE name='羊栖菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 5, 0 FROM ingredient WHERE name='羊栖菜';
INSERT IGNORE INTO ingredient_nutrition(ingredient_id, metric_id, value) SELECT id, 6, 17 FROM ingredient WHERE name='羊栖菜';

COMMIT;

-- 验证：SELECT COUNT(*) FROM ingredient;  -- 目标 ~470
-- 抽查细分：SELECT n.value FROM ingredient_nutrition n JOIN ingredient i ON i.id=n.ingredient_id WHERE i.name='小米椒' ORDER BY n.metric_id;
-- 抽查 UTF-8：SELECT name,HEX(name) FROM ingredient WHERE name='小米椒';  -- 应为 E5 B0 8F ... 非 0001
-- 孤儿：SELECT COUNT(*) FROM dish_ingredient di LEFT JOIN ingredient i ON i.id=di.ingredient_id WHERE i.id IS NULL;  -- 应为 0