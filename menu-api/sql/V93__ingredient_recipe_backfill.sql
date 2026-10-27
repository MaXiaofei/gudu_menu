-- ============================================================
-- V93__ingredient_recipe_backfill.sql
-- 咕嘟小食单：下厨房菜谱用料补全新增的 82 种常见食材 + 营养数据
--
-- 背景：2026-10-27 用 scripts/recipe-fix 补全下厨房导入菜谱的用料时，生产库已新增这 82 种食材
--   （生姜、生抽、白砂糖、食用油、低筋面粉……）。本迁移让预发/新库与生产一致，并补营养数据。
--
-- 营养数据（每 100g 可食部，只填 能量/蛋白质/脂肪/碳水，GI 与糖留空）：
--   - 「参照」：与食材库已有条目成分相当的，直接复制该条目的数值（来源同 V56《中国食物成分表·第6版》）
--       白砂糖、糖粉←绵白糖；生抽、蒸鱼豉油←酱油；食用油、花椒油←花生油；低筋面粉←富强粉；
--       高筋面粉←标准粉；糯米粉←糯米；黑胡椒←白胡椒；黄酒←料酒；意大利面←挂面；辣椒粉←干辣椒
--   - 生姜：《中国食物成分表》姜（黄姜）46 kcal / 蛋白 1.3 / 脂肪 0.6 / 碳水 10.3
--   - 其余（香料、酱料、酒、烘焙辅料等）用量极小或无可靠数据，留空（营养计算按 0）
--
-- 幂等：食材 NOT EXISTS by name；营养 NOT EXISTS by (食材, 指标)；饮料分类按 V53 规则标饮料零食。
-- ============================================================
SET NAMES utf8mb4;
START TRANSACTION;

-- 1) 食材（name, 采购分类, edible, icon_key）
INSERT INTO ingredient(name, purchase_category_id, edible, icon_key)
SELECT v.name, (SELECT d.id FROM sys_dict d WHERE d.dict_group='purchase_category' AND d.name=v.cat LIMIT 1), v.ed, v.icon
FROM (
  VALUES
  ROW('榨菜','蔬菜',1,'cabbage'),
  ROW('欧芹','蔬菜',1,'cabbage'),
  ROW('泡菜','蔬菜',1,'cabbage'),
  ROW('生姜','蔬菜',1,'ginger'),
  ROW('芽菜','蔬菜',1,'cabbage'),
  ROW('酸菜','蔬菜',1,'cabbage'),
  ROW('青蒜','蔬菜',1,'garlic'),
  ROW('培根','畜禽肉',1,'pork'),
  ROW('猪棒骨','畜禽肉',1,'ribs'),
  ROW('肉松','畜禽肉',1,'pork'),
  ROW('鱼丸','水产海鲜',1,'fish'),
  ROW('日本豆腐','豆制品',1,'tofu'),
  ROW('红豆沙','豆制品',1,'tofu'),
  ROW('奶粉','乳制品',1,'milk'),
  ROW('椰浆','乳制品',1,'milk'),
  ROW('丁香','调味料',1,'sauce'),
  ROW('五香粉','调味料',1,'sauce'),
  ROW('剁椒','调味料',1,'sauce'),
  ROW('十三香','调味料',1,'sauce'),
  ROW('可可粉','调味料',1,'sauce'),
  ROW('吉利丁片','调味料',1,'sauce'),
  ROW('咖啡粉','调味料',1,'sauce'),
  ROW('咖喱','调味料',1,'sauce'),
  ROW('啤酒','调味料',1,'sauce'),
  ROW('小苏打','调味料',1,'sauce'),
  ROW('小茴香','调味料',1,'sauce'),
  ROW('山奈','调味料',1,'sauce'),
  ROW('巧克力酱','调味料',1,'sauce'),
  ROW('抹茶粉','调味料',1,'sauce'),
  ROW('排骨酱','调味料',1,'ribs'),
  ROW('新奥尔良腌料','调味料',1,'sauce'),
  ROW('朗姆酒','调味料',1,'sauce'),
  ROW('果冻粉','调味料',1,'sauce'),
  ROW('柱侯酱','调味料',1,'sauce'),
  ROW('桂皮','调味料',1,'sauce'),
  ROW('泡打粉','调味料',1,'sauce'),
  ROW('泡椒','调味料',1,'sauce'),
  ROW('甜辣酱','调味料',1,'sauce'),
  ROW('生抽','调味料',1,'sauce'),
  ROW('白砂糖','调味料',1,'sauce'),
  ROW('白酒','调味料',1,'sauce'),
  ROW('百里香','调味料',1,'sauce'),
  ROW('砂仁','调味料',1,'sauce'),
  ROW('糖桂花','调味料',1,'sauce'),
  ROW('糖粉','调味料',1,'sauce'),
  ROW('红酒','调味料',1,'sauce'),
  ROW('肉骨茶料包','调味料',1,'sauce'),
  ROW('花椒油','调味料',1,'oil'),
  ROW('草果','调味料',1,'sauce'),
  ROW('蒸鱼豉油','调味料',1,'sauce'),
  ROW('蓝莓酱','调味料',1,'sauce'),
  ROW('豆蔻','调味料',1,'sauce'),
  ROW('辣椒粉','调味料',1,'pepper'),
  ROW('辣椒酱','调味料',1,'pepper'),
  ROW('迷迭香','调味料',1,'sauce'),
  ROW('酵母','调味料',1,'sauce'),
  ROW('食用油','调味料',1,'oil'),
  ROW('食用色素','调味料',1,'sauce'),
  ROW('香叶','调味料',1,'sauce'),
  ROW('香茅','调味料',1,'sauce'),
  ROW('香草精','调味料',1,'sauce'),
  ROW('鱼露','调味料',1,'fish'),
  ROW('黄酒','调味料',1,'sauce'),
  ROW('黑胡椒','调味料',1,'sauce'),
  ROW('低筋面粉','谷物主食',1,'rice'),
  ROW('冷面','谷物主食',1,'rice'),
  ROW('千层面皮','谷物主食',1,'rice'),
  ROW('意大利面','谷物主食',1,'rice'),
  ROW('手指饼干','谷物主食',1,'rice'),
  ROW('杏仁粉','谷物主食',1,'rice'),
  ROW('消化饼干','谷物主食',1,'rice'),
  ROW('糯米粉','谷物主食',1,'rice'),
  ROW('西米','谷物主食',1,'rice'),
  ROW('酥皮','谷物主食',1,'rice'),
  ROW('面包糠','谷物主食',1,'rice'),
  ROW('高筋面粉','谷物主食',1,'rice'),
  ROW('枸杞','坚果',1,'rice'),
  ROW('椰丝','坚果',1,'rice'),
  ROW('蔓越莓干','坚果',1,'rice'),
  ROW('陈皮','坚果',1,'rice'),
  ROW('可乐','饮料',2,'milk'),
  ROW('茶叶','饮料',2,'milk')
) AS v(name, cat, ed, icon)
WHERE NOT EXISTS (SELECT 1 FROM ingredient i WHERE i.name = v.name AND i.deleted = 0);

-- 生产上由脚本新增时 edible 统一记成了 1，饮料分类按 V53 规则改为饮料零食
UPDATE ingredient i
JOIN sys_dict d ON d.id = i.purchase_category_id AND d.dict_group = 'purchase_category' AND d.name = '饮料'
SET i.edible = 2
WHERE i.edible = 1 AND i.name IN ('可乐', '茶叶');

-- 2) 营养：参照已有条目复制
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value)
SELECT t.id, n.metric_id, n.value
FROM (
  VALUES
  ROW('白砂糖','绵白糖'), ROW('糖粉','绵白糖'), ROW('生抽','酱油'), ROW('蒸鱼豉油','酱油'),
  ROW('食用油','花生油'), ROW('花椒油','花生油'), ROW('低筋面粉','富强粉'), ROW('高筋面粉','标准粉'),
  ROW('糯米粉','糯米'), ROW('黑胡椒','白胡椒'), ROW('黄酒','料酒'), ROW('意大利面','挂面'),
  ROW('辣椒粉','干辣椒')
) AS r(target, src)
JOIN ingredient t ON t.name = r.target AND t.deleted = 0
JOIN ingredient s ON s.name = r.src AND s.deleted = 0
JOIN ingredient_nutrition n ON n.ingredient_id = s.id
JOIN nutrition_metric m ON m.id = n.metric_id AND m.name IN ('calorie','protein','fat','carb')
WHERE NOT EXISTS (SELECT 1 FROM ingredient_nutrition x WHERE x.ingredient_id = t.id AND x.metric_id = n.metric_id);

-- 3) 营养：生姜
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value)
SELECT i.id, m.id,
  CASE m.name WHEN 'calorie' THEN 46 WHEN 'protein' THEN 1.3 WHEN 'fat' THEN 0.6 WHEN 'carb' THEN 10.3 END
FROM ingredient i
JOIN nutrition_metric m ON m.name IN ('calorie','protein','fat','carb')
WHERE i.name = '生姜' AND i.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM ingredient_nutrition x WHERE x.ingredient_id = i.id AND x.metric_id = m.id);

COMMIT;
