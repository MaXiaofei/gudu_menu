-- V52: unit 字典补下厨房常用单位（导入互通：自由文本单位映射到受控字典，幂等 INSERT IGNORE）
-- 背景：下厨房用料单位是自由文本（小勺/茶匙/两/撮…），导入时按名匹配字典，缺的自动补录；
--       预置常见单位提高匹配率，避免每次导入都现补。

INSERT IGNORE INTO sys_dict(dict_group, name) VALUES
  ('unit','小勺'),('unit','大勺'),('unit','茶匙'),('unit','汤匙'),('unit','小碗'),
  ('unit','两'),('unit','撮'),('unit','段'),('unit','瓣'),('unit','粒'),
  ('unit','碗'),('unit','片'),('unit','根'),('unit','只'),('unit','条');
