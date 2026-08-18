-- ============================================================
-- V58: 标签同义合并（修复菜谱页第一排筛选不生效，2026-08-18）
--
-- 根因：字典双词表分裂——历史/导入操作补录了同义新词条：
--   「下饭菜」(63 道菜关联) vs 预置「下饭」(0 道关联) → 点「下饭」筛选 0 结果
--   「家常」(孤立词条) vs 「家常菜」(V57 定稿的 tag 合法项)
-- 处理：dish_dict 关联改指预置词后删除补录词条。
-- 注意（对齐 V57 决策）：西餐/日料/韩餐/清真是 tag 组合法项（cuisine 只留
--   十大中国菜系），本迁移不动它们。
-- 幂等：按 name 定位（防 id 漂移），UPDATE/DELETE 天然幂等。
-- ============================================================

-- 1) 下饭菜 → 下饭
SET @from1 := (SELECT id FROM sys_dict WHERE dict_group='tag' AND name='下饭菜' LIMIT 1);
SET @to1   := (SELECT id FROM sys_dict WHERE dict_group='tag' AND name='下饭'   LIMIT 1);
UPDATE dish_dict SET dict_id = @to1 WHERE @from1 IS NOT NULL AND @to1 IS NOT NULL
  AND rel_type='tag' AND dict_id = @from1;
DELETE FROM sys_dict WHERE @from1 IS NOT NULL AND id = @from1 AND dict_group='tag';

-- 2) 家常 → 家常菜
SET @from2 := (SELECT id FROM sys_dict WHERE dict_group='tag' AND name='家常'   LIMIT 1);
SET @to2   := (SELECT id FROM sys_dict WHERE dict_group='tag' AND name='家常菜' LIMIT 1);
UPDATE dish_dict SET dict_id = @to2 WHERE @from2 IS NOT NULL AND @to2 IS NOT NULL
  AND rel_type='tag' AND dict_id = @from2;
DELETE FROM sys_dict WHERE @from2 IS NOT NULL AND id = @from2 AND dict_group='tag';

-- 验证：
--   SELECT s.name, COUNT(*) FROM dish_dict d JOIN sys_dict s ON s.id=d.dict_id
--     WHERE d.rel_type='tag' GROUP BY s.name;  -- 不应再有 下饭菜/家常
