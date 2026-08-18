-- ============================================================
-- V57__cuisine_clean_to_tag.sql
-- 菜系字典清洗：cuisine 只保留国家认可的中国菜系
--   （八大菜系：川/鲁/粤/苏/浙/闽/湘/徽 + 公认地方菜系：东北/西北），
--   脏菜系（家常菜/清真/日料/韩餐/西餐——非国家认可菜系体系）合并到 tag，
--   菜谱关联信息不丢（dish_dict 改挂 tag，同名或映射名）。
--
-- 幂等：uk_group_name 唯一索引 + INSERT IGNORE + 按名匹配，可重复执行。
-- 执行后建议：POST /dish/vector/rebuild 重建向量库（文档含菜系名文本）。
-- ============================================================
START TRANSACTION;

-- 1) 确保目标 tag 存在（家常已有同名，其余新建；重复执行安全）
INSERT IGNORE INTO sys_dict(dict_group, name, sort) VALUES
  ('tag','家常', 90),
  ('tag','清真', 80),
  ('tag','日料', 70),
  ('tag','韩餐', 60),
  ('tag','西餐', 50);

-- 2) 脏菜系关联迁移：dish_dict.rel_type cuisine→tag，dict_id 改挂目标 tag
--    （家常菜→家常，其余同名并入 tag；按名字 join，不硬编码 id）
UPDATE dish_dict d
JOIN sys_dict c ON c.id = d.dict_id AND c.dict_group = 'cuisine'
JOIN sys_dict t ON t.dict_group = 'tag'
  AND t.name = CASE c.name
    WHEN '家常菜' THEN '家常'
    ELSE c.name
  END
SET d.rel_type = 'tag', d.dict_id = t.id
WHERE c.name IN ('家常菜', '清真', '日料', '韩餐', '西餐');

-- 3) 删除脏菜系字典条目
DELETE FROM sys_dict
WHERE dict_group = 'cuisine' AND name IN ('家常菜', '清真', '日料', '韩餐', '西餐');

COMMIT;

-- 验证：
--   SELECT id, dict_group, name, sort FROM sys_dict WHERE dict_group='cuisine' ORDER BY sort, id;
--   SELECT d.dict_id, s.name, COUNT(*) FROM dish_dict d
--     JOIN sys_dict s ON s.id = d.dict_id AND s.dict_group='tag'
--     WHERE d.rel_type='tag' AND s.name IN ('家常','清真','日料','韩餐','西餐')
--     GROUP BY d.dict_id, s.name;
