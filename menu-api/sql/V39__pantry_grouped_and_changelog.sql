-- V39: 库存三色分组 + 变动流水（阈值从 pantry 行级挪到 ingredient 食材级）
-- 阈值按食材聚合判（同食材多笔合一），pantry 行级 low_threshold 迁移到 ingredient 后删除
ALTER TABLE ingredient ADD COLUMN low_threshold DECIMAL(10,2) NOT NULL DEFAULT 0
  COMMENT '库存警戒阈值（食材级，按默认单位计）' AFTER price;

-- 迁移：把每个食材现有 pantry 批次的最大阈值搬过来（多批次取最大值当默认警戒线）
UPDATE ingredient i SET low_threshold = (
  SELECT COALESCE(MAX(p.low_threshold), 0) FROM pantry p
  WHERE p.ingredient_id = i.id AND p.deleted = 0
) WHERE EXISTS (SELECT 1 FROM pantry p WHERE p.ingredient_id = i.id AND p.deleted = 0);

-- 删 pantry 旧字段（阈值已挪到 ingredient）
ALTER TABLE pantry DROP COLUMN low_threshold;

-- 库存变动流水表：支撑「每行来源标签」+「详情页最近6条操作明细」
CREATE TABLE pantry_change_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  ingredient_id BIGINT      NOT NULL COMMENT '食材 ingredient.id',
  source      VARCHAR(16)  NOT NULL COMMENT '来源: cook做菜 / purchase采购 / inventory盘点 / manual手动',
  delta       DECIMAL(12,2) NOT NULL COMMENT '变动量（正入负出，克）',
  amount_after DECIMAL(12,2) NULL    COMMENT '变动后该食材合计（克）',
  source_note VARCHAR(64)  NULL      COMMENT '来源备注（手动：朋友送/赠品/旧库存补登）',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_log_ingredient (ingredient_id, create_time DESC)
) COMMENT '库存变动流水';
