-- V42: 手动库存（ingredient_stock 3 档 + stock_log 简版流水）
-- 背景：docs/pantry-shopping-redesign.md v0.5 —— 库存不做自动扣减、不做克数，改 3 档模糊级别
-- pantry 表保留不再更新（admin 过渡读，P5 迁移 admin 后废弃）

-- 1) ingredient_stock：每食材一行 3 档
CREATE TABLE IF NOT EXISTS ingredient_stock (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  ingredient_id BIGINT      NOT NULL COMMENT '食材 ingredient.id',
  level         VARCHAR(16) NOT NULL DEFAULT 'ENOUGH' COMMENT 'ENOUGH充足/LOW快用完/NONE没有',
  update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_stock_ingredient (ingredient_id)
) COMMENT '食材库存档位（模糊 3 档，不做克数）';

-- 2) stock_log：简版流水（用完了/用了一些/采购入库/手动修正）
CREATE TABLE IF NOT EXISTS stock_log (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  ingredient_id BIGINT       NOT NULL COMMENT '食材 ingredient.id',
  action        VARCHAR(16)  NOT NULL COMMENT 'cook用完了/cook_partial用了一些/purchase采购/manual手动',
  note          VARCHAR(255) NULL     COMMENT '备注（手动：朋友送/赠品/旧库存补登）',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_stock_log_ingredient (ingredient_id, create_time DESC)
) COMMENT '库存档位变动流水';

-- 3) 存量映射：pantry → ingredient_stock（幂等：已存在该食材则跳过；近似映射，用户后续可改）
--    规则：Σgrams ≤ 0 → NONE；> 0 → ENOUGH；grams 全 NULL → 按 Σamount 近似（>0 即 ENOUGH）。
--    （V55 删除 ingredient.low_threshold 后不再区分 LOW，LOW 档由用户手动改）
INSERT INTO ingredient_stock (ingredient_id, level, update_time)
SELECT p.ingredient_id,
       CASE
         WHEN SUM(p.grams) IS NULL THEN
           CASE WHEN COALESCE(SUM(p.amount), 0) > 0 THEN 'ENOUGH' ELSE 'NONE' END
         WHEN SUM(p.grams) <= 0 THEN 'NONE'
         ELSE 'ENOUGH'
       END,
       MAX(p.update_time)
FROM pantry p
WHERE p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM ingredient_stock s WHERE s.ingredient_id = p.ingredient_id)
GROUP BY p.ingredient_id;
