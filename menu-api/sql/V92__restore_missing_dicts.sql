-- ============================================================
-- V92：补齐生产库缺失的两组字典（sys_dict）
--
-- 背景：2026-10-27 核对生产库，发现以下两组完全不存在（迁移文件里都有，生产库初始化基线与迁移不一致）：
--   - review_dimension：V12 的 口味 / 难度 / 营养均衡 / 外观。缺失 → 小程序点评页没有维度打分项；
--   - purchase_unit：V21 的 斤 / 把 / 个 / 少许 / 盒 / 袋 / 块 / 克。缺失 → 采购清单无法选采购单位。
-- 幂等：逐条按 (dict_group, name) 判断，已存在（含后台改过排序的）不动，只补缺失项。
-- ============================================================
INSERT INTO sys_dict (dict_group, name, sort)
SELECT d.grp, d.name, d.sort
FROM (SELECT 'review_dimension' AS grp, '口味' AS name, 1 AS sort
      UNION ALL SELECT 'review_dimension', '难度', 2
      UNION ALL SELECT 'review_dimension', '营养均衡', 3
      UNION ALL SELECT 'review_dimension', '外观', 4
      UNION ALL SELECT 'purchase_unit', '斤', 1
      UNION ALL SELECT 'purchase_unit', '把', 2
      UNION ALL SELECT 'purchase_unit', '个', 3
      UNION ALL SELECT 'purchase_unit', '少许', 4
      UNION ALL SELECT 'purchase_unit', '盒', 5
      UNION ALL SELECT 'purchase_unit', '袋', 6
      UNION ALL SELECT 'purchase_unit', '块', 7
      UNION ALL SELECT 'purchase_unit', '克', 8) d
WHERE NOT EXISTS (SELECT 1 FROM sys_dict s WHERE s.dict_group = d.grp AND s.name = d.name);
