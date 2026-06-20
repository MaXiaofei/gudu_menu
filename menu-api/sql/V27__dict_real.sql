-- ============================================================
-- V27__dict_real.sql
-- 烟火小食单：配置中心灌真实字典（参考下厨房等家庭做菜分类）
--
-- 背景：V02 种子（cuisine/tag/category/menu_type/unit/purchase_category）在
--      yanhuo / prod 库存在 UTF-8 双重编码 mojibake（latin1 误读为 utf8 再编码），
--      导致前端下拉显示乱码；yanhuo_test 库正常。本迁移两步走：
--
--   STEP 1  原地修复 V02 mojibake 行（按 id UPDATE，仅当 HEX(name) 与正确 UTF-8 不一致）：
--           - 幂等：已是正确 UTF-8（如 yanhuo_test）则 WHERE 不命中 → no-op。
--           - 不 DELETE、不改 id → 食材 purchase_category_id / unit_id 引用全部保留。
--           - 仅修 cuisine/tag/category/menu_type/unit/purchase_category；
--             audience（V26 已扩，用户明确不动）、role（用户明确排除）不碰。
--
--   STEP 2  补真实分类（INSERT...SELECT WHERE NOT EXISTS by group+name，绝不 DELETE）：
--           cuisine 八大菜系+地方 / tag 下厨房真实标签 / category 主食小吃饮品酱料 /
--           menu_type 减脂增肌工作日周末 / purchase_category 谷物坚果干菌藻速冻饮料 /
--           unit 计量单位补全。
--           meal / review_dimension / gender 已齐，不补。
--
-- 字段对应 sys_dict(dict_group, name, sort)；utf8mb4。
-- ============================================================

START TRANSACTION;

-- ============================================================
-- STEP 1：原地修复 V02 mojibake（id 1-31，中文部分）
--          仅当当前 HEX(name) ≠ 正确 UTF-8 HEX 时才 UPDATE；已是正确编码则 no-op。
-- ============================================================

-- cuisine
UPDATE sys_dict SET name='鲁菜', sort=10 WHERE id=1  AND dict_group='cuisine'            AND HEX(name)<>'E9B281E88F9C';
UPDATE sys_dict SET name='川菜', sort=20 WHERE id=2  AND dict_group='cuisine'            AND HEX(name)<>'E5B79DE88F9C';
UPDATE sys_dict SET name='粤菜', sort=30 WHERE id=3  AND dict_group='cuisine'            AND HEX(name)<>'E7B2A4E88F9C';

-- tag
UPDATE sys_dict SET name='家常', sort=10 WHERE id=4  AND dict_group='tag'                AND HEX(name)<>'E5AEB6E5B8B8';
UPDATE sys_dict SET name='快手菜',sort=20 WHERE id=5  AND dict_group='tag'                AND HEX(name)<>'E5BFABE6898BE88F9C';
UPDATE sys_dict SET name='下饭', sort=30 WHERE id=6  AND dict_group='tag'                AND HEX(name)<>'E4B88BE9A5AD';
UPDATE sys_dict SET name='清淡', sort=40 WHERE id=7  AND dict_group='tag'                AND HEX(name)<>'E6B885E6B7A1';

-- category
UPDATE sys_dict SET name='热菜', sort=10 WHERE id=8  AND dict_group='category'           AND HEX(name)<>'E783ADE88F9C';
UPDATE sys_dict SET name='凉菜', sort=20 WHERE id=9  AND dict_group='category'           AND HEX(name)<>'E58789E88F9C';
UPDATE sys_dict SET name='汤羹', sort=30 WHERE id=10 AND dict_group='category'           AND HEX(name)<>'E6B1A4E7BEB9';
UPDATE sys_dict SET name='甜品', sort=60 WHERE id=11 AND dict_group='category'           AND HEX(name)<>'E7949CE59381';

-- menu_type
UPDATE sys_dict SET name='日常', sort=10 WHERE id=12 AND dict_group='menu_type'          AND HEX(name)<>'E697A5E5B8B8';
UPDATE sys_dict SET name='家宴', sort=20 WHERE id=13 AND dict_group='menu_type'          AND HEX(name)<>'E5AEB6E5AEB4';
UPDATE sys_dict SET name='节日', sort=30 WHERE id=14 AND dict_group='menu_type'          AND HEX(name)<>'E88A82E697A5';
UPDATE sys_dict SET name='宝宝餐',sort=40 WHERE id=15 AND dict_group='menu_type'          AND HEX(name)<>'E5AE9DE5AE9DE9A490';

-- unit
UPDATE sys_dict SET name='个', sort=30 WHERE id=22 AND dict_group='unit'                 AND HEX(name)<>'E4B8AA';
UPDATE sys_dict SET name='把', sort=40 WHERE id=23 AND dict_group='unit'                 AND HEX(name)<>'E68A8A';
-- id 20/21 (g/ml) 为 ASCII，无 mojibake，仅规范化 sort
UPDATE sys_dict SET sort=10 WHERE id=20 AND dict_group='unit' AND name='g';
UPDATE sys_dict SET sort=20 WHERE id=21 AND dict_group='unit' AND name='ml';

-- purchase_category
UPDATE sys_dict SET name='蔬菜',    sort=10 WHERE id=24 AND dict_group='purchase_category' AND HEX(name)<>'E894ACE88F9C';
UPDATE sys_dict SET name='畜禽肉',  sort=20 WHERE id=25 AND dict_group='purchase_category' AND HEX(name)<>'E7959CE7A6BDE88289';
UPDATE sys_dict SET name='水产海鲜',sort=30 WHERE id=26 AND dict_group='purchase_category' AND HEX(name)<>'E6B0B4E4BAA7E6B5B7E9B29C';
UPDATE sys_dict SET name='蛋类',    sort=40 WHERE id=27 AND dict_group='purchase_category' AND HEX(name)<>'E89B8BE7B1BB';
UPDATE sys_dict SET name='豆制品',  sort=50 WHERE id=28 AND dict_group='purchase_category' AND HEX(name)<>'E8B186E588B6E59381';
UPDATE sys_dict SET name='乳制品',  sort=60 WHERE id=29 AND dict_group='purchase_category' AND HEX(name)<>'E4B9B3E588B6E59381';
UPDATE sys_dict SET name='调味料',  sort=70 WHERE id=30 AND dict_group='purchase_category' AND HEX(name)<>'E8B083E591B3E69699';
UPDATE sys_dict SET name='水果',    sort=80 WHERE id=31 AND dict_group='purchase_category' AND HEX(name)<>'E6B0B4E69E9C';

-- ============================================================
-- STEP 2：补真实分类（INSERT...SELECT WHERE NOT EXISTS by group+name）
-- ============================================================

-- cuisine 菜系：八大菜系（鲁/川/粤 已在 V02，补苏/浙/闽/徽/湘）+ 地方/异国
INSERT INTO sys_dict(dict_group, name, sort)
SELECT 'cuisine', v.name, v.sort
FROM (
  VALUES
  ROW('苏菜',  40),
  ROW('浙菜',  50),
  ROW('闽菜',  60),
  ROW('徽菜',  70),
  ROW('湘菜',  80),
  ROW('家常菜',90),
  ROW('东北菜',100),
  ROW('西北菜',110),
  ROW('清真',  120),
  ROW('日料',  130),
  ROW('韩餐',  140),
  ROW('西餐',  150)
) AS v(name, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict d WHERE d.dict_group='cuisine' AND d.name=v.name
);

-- tag 标签（参考下厨房家庭做菜真实标签；家常/快手菜/下饭/清淡 已在 V02）
INSERT INTO sys_dict(dict_group, name, sort)
SELECT 'tag', v.name, v.sort
FROM (
  VALUES
  ROW('快手',  25),
  ROW('重口味',45),
  ROW('素菜',  50),
  ROW('荤菜',  60),
  ROW('汤',    70),
  ROW('早餐',  80),
  ROW('午餐',  90),
  ROW('晚餐',  100),
  ROW('夜宵',  110),
  ROW('宝宝',  120),
  ROW('老人',  130),
  ROW('减脂',  140),
  ROW('增肌',  150),
  ROW('补钙',  160),
  ROW('补血',  170),
  ROW('明目',  180),
  ROW('养胃',  190),
  ROW('低卡',  200),
  ROW('低脂',  210),
  ROW('低糖',  220),
  ROW('无辣',  230),
  ROW('微辣',  240),
  ROW('中辣',  250),
  ROW('特辣',  260),
  ROW('素食',  270),
  ROW('卤味',  280),
  ROW('烧烤',  290),
  ROW('蒸',    300),
  ROW('炒',    310),
  ROW('炖',    320),
  ROW('凉拌',  330),
  ROW('烘焙',  340),
  ROW('节日',  350)
) AS v(name, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict d WHERE d.dict_group='tag' AND d.name=v.name
);

-- category 分类：热菜/凉菜/汤羹/甜品 已在 V02，补主食/小吃/饮品/酱料/早餐
INSERT INTO sys_dict(dict_group, name, sort)
SELECT 'category', v.name, v.sort
FROM (
  VALUES
  ROW('主食', 40),
  ROW('小吃', 50),
  ROW('饮品', 70),
  ROW('酱料', 80),
  ROW('早餐', 90)
) AS v(name, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict d WHERE d.dict_group='category' AND d.name=v.name
);

-- menu_type 菜单类型：日常/家宴/节日/宝宝餐 已在 V02，补减脂餐/增肌餐/工作日/周末
INSERT INTO sys_dict(dict_group, name, sort)
SELECT 'menu_type', v.name, v.sort
FROM (
  VALUES
  ROW('减脂餐',  50),
  ROW('增肌餐',  60),
  ROW('工作日',  70),
  ROW('周末',    80)
) AS v(name, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict d WHERE d.dict_group='menu_type' AND d.name=v.name
);

-- purchase_category 采购品类：蔬菜/畜禽肉/水产海鲜/蛋类/豆制品/乳制品/调味料/水果 已在 V02，
--   补谷物主食/坚果/干菌藻/速冻/饮料
INSERT INTO sys_dict(dict_group, name, sort)
SELECT 'purchase_category', v.name, v.sort
FROM (
  VALUES
  ROW('谷物主食', 90),
  ROW('坚果',    100),
  ROW('干菌藻',  110),
  ROW('速冻',    120),
  ROW('饮料',    130)
) AS v(name, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict d WHERE d.dict_group='purchase_category' AND d.name=v.name
);

-- unit 计量单位：g/ml/个/把 已在 V02，补 斤/两/千克/升/包/盒/袋/勺/少许/根/颗/瓣/片
INSERT INTO sys_dict(dict_group, name, sort)
SELECT 'unit', v.name, v.sort
FROM (
  VALUES
  ROW('斤',   50),
  ROW('两',   60),
  ROW('千克', 70),
  ROW('升',   80),
  ROW('包',   90),
  ROW('盒',  100),
  ROW('袋',  110),
  ROW('勺',  120),
  ROW('少许',130),
  ROW('根',  140),
  ROW('颗',  150),
  ROW('瓣',  160),
  ROW('片',  170)
) AS v(name, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict d WHERE d.dict_group='unit' AND d.name=v.name
);

COMMIT;
