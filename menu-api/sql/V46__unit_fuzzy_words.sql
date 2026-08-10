-- V46: unit 字典补量词单位（适量/少许/一小把）
-- 背景：DESIGN.md §16.3 —— 写菜谱用料用量是自由文本，「适量/少许/一小把」
--   也作为单位在 sys_dict(group=unit) 维护，写菜谱解析时绑定 unit_id
--   （amount 为空，不折算克数，详情页按单位名回显原文）。
-- 幂等：INSERT ... SELECT ... WHERE NOT EXISTS（同名不重复插）

INSERT INTO sys_dict(dict_group, name)
SELECT 'unit', '适量' WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_group='unit' AND name='适量');
INSERT INTO sys_dict(dict_group, name)
SELECT 'unit', '少许' WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_group='unit' AND name='少许');
INSERT INTO sys_dict(dict_group, name)
SELECT 'unit', '一小把' WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_group='unit' AND name='一小把');
