-- V38: shopping_list 加 source_menu_id（采购清单食集溯源，评审 §9 / Plan E）
-- 铁律「采购从食集派生记溯源」：menu 来源此前丢了 sourceId，现补回
ALTER TABLE shopping_list ADD COLUMN source_menu_id BIGINT NULL COMMENT '来源食集 menu.id（plan/custom 留空）' AFTER source_plan_id;
CREATE INDEX idx_shopping_list_source_menu ON shopping_list(source_menu_id);
