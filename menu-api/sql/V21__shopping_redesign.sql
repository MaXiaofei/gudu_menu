-- V21: 采购清单重新设计 —— 三数据源(menu/dish/plan) + 用户填采购量+采购单位(生活单位)。
--
-- 设计变化：
--  1) 数据源三选：菜单 menu_dish / 菜品 dish(多选) / 周计划 meal_plan_item；
--  2) 采购单位走 sys_dict(group=purchase_unit)：斤/把/个/少许/盒/袋/块/克（中文，用户选）；
--  3) shopping_item 加 purchase_amount(用户填采购量) + purchase_unit_id(采购单位 dict id)
--     + reference_grams(参考克数：菜谱用量合计，仅提示)；
--  4) total_amount/unit_id/purchase_category_id 保留(参考用，不删，避免 ALTER 风险)，新代码读 reference_grams。
--
-- 不 DROP 旧列，增量 ALTER。

-- ============ 采购单位字典（group=purchase_unit）============
INSERT INTO sys_dict(dict_group, name, sort) VALUES
  ('purchase_unit', '斤', 1),
  ('purchase_unit', '把', 2),
  ('purchase_unit', '个', 3),
  ('purchase_unit', '少许', 4),
  ('purchase_unit', '盒', 5),
  ('purchase_unit', '袋', 6),
  ('purchase_unit', '块', 7),
  ('purchase_unit', '克', 8);

-- ============ shopping_item 加采购维度字段 ============
ALTER TABLE shopping_item ADD COLUMN purchase_amount  DECIMAL(10,2) NULL;
ALTER TABLE shopping_item ADD COLUMN purchase_unit_id BIGINT       NULL;
ALTER TABLE shopping_item ADD COLUMN reference_grams  DECIMAL(10,2) NULL;
