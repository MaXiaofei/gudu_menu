-- ============================================================
-- V58: 标签同义合并（修复菜谱页第一排筛选不生效，2026-08-18）
--
-- 根因：字典双词表分裂——「下饭菜」词条被补录（63 道菜关联）而预置
--   「下饭」词条在早前清洗中被误删 → 点「下饭」筛选 0 结果；
--   「家常」孤立词条 vs 「家常菜」重复。
-- 幂等 v2：先确保目标词条存在（缺失则补），再合并词条 + 孤儿关联兜底
--   （首跑时「下饭」缺失导致只删未迁，本版把孤儿 dict_id=5150 一并迁走）。
-- 对齐 V57 决策：西餐/日料/韩餐/清真是 tag 组合法项，不动。
-- ============================================================

-- 0) 确保目标词条存在（「下饭」缺失则补回；INSERT...SELECT WHERE NOT EXISTS 幂等）
INSERT INTO sys_dict(dict_group, name, sort)
SELECT 'tag', '下饭', 6 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_group='tag' AND name='下饭');

-- 1) 下饭菜 → 下饭（词条仍在的常规路径 + 孤儿 dict_id=5150 兜底）
SET @from1 := (SELECT id FROM sys_dict WHERE dict_group='tag' AND name='下饭菜' LIMIT 1);
SET @to1   := (SELECT id FROM sys_dict WHERE dict_group='tag' AND name='下饭'   LIMIT 1);
UPDATE dish_dict SET dict_id = @to1
  WHERE @to1 IS NOT NULL AND rel_type='tag'
    AND (dict_id = @from1 OR dict_id = 5150);
DELETE FROM sys_dict WHERE @from1 IS NOT NULL AND id = @from1 AND dict_group='tag';

-- 2) 家常 → 家常菜
SET @from2 := (SELECT id FROM sys_dict WHERE dict_group='tag' AND name='家常'   LIMIT 1);
SET @to2   := (SELECT id FROM sys_dict WHERE dict_group='tag' AND name='家常菜' LIMIT 1);
UPDATE dish_dict SET dict_id = @to2
  WHERE @from2 IS NOT NULL AND @to2 IS NOT NULL AND rel_type='tag' AND dict_id = @from2;
DELETE FROM sys_dict WHERE @from2 IS NOT NULL AND id = @from2 AND dict_group='tag';

-- 验证：
--   SELECT s.name, COUNT(*) FROM dish_dict d JOIN sys_dict s ON s.id=d.dict_id
--     WHERE d.rel_type='tag' GROUP BY s.name;  -- 「下饭」应有 63 道左右，无孤儿
--   SELECT COUNT(*) FROM dish_dict d LEFT JOIN sys_dict s ON s.id=d.dict_id
--     WHERE d.rel_type='tag' AND s.id IS NULL;  -- 孤儿应为 0
