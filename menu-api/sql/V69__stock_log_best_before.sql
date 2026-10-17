-- ============================================================
-- V69: stock_log 加 best_before 快照（prototype-ui-rollout 0.7「本月少扔」）
-- 做菜用掉食材时记下当时的保质期（用完后 ingredient_stock.best_before 会被清空，事后无从判断）。
-- 「本月少扔」= 本月做菜流水（cook/cook_partial）中，使用日在保质期前 3 天内（含当天）的条数。
-- 存量流水无快照，统计从上线后开始累计。幂等：information_schema 判断。
-- ============================================================
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_log' AND COLUMN_NAME = 'best_before');
SET @s := IF(@c = 0,
  'ALTER TABLE stock_log ADD COLUMN best_before DATE NULL COMMENT ''变动前保质期快照（做菜用掉时记录）'' AFTER after_level',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
