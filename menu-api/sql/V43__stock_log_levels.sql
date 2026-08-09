-- V43: stock_log 加档位前后值 + 溯源（撤回入库用）
-- 背景：P2 B1 —— 撤回入库需恢复"本次变动前档位"（before_level）；采购流水按 ref_id 溯源到 shopping_item
-- 幂等：ALTER 用 information_schema 判存在（照 V36 范式）

-- 1) before_level：变动前档位（ENOUGH/LOW/NONE，新建档为 NULL）
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='stock_log' AND COLUMN_NAME='before_level');
SET @s1 := IF(@c1=0,'ALTER TABLE stock_log ADD COLUMN before_level VARCHAR(16) NULL COMMENT ''变动前档位（新建档为 NULL）'' AFTER action','SELECT 1');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;

-- 2) after_level：变动后档位
SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='stock_log' AND COLUMN_NAME='after_level');
SET @s2 := IF(@c2=0,'ALTER TABLE stock_log ADD COLUMN after_level VARCHAR(16) NULL COMMENT ''变动后档位'' AFTER before_level','SELECT 1');
PREPARE p2 FROM @s2; EXECUTE p2; DEALLOCATE PREPARE p2;

-- 3) ref_id：溯源（采购入库=shopping_item.id；撤回=shopping_item.id），撤回入库按它查流水
SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='stock_log' AND COLUMN_NAME='ref_id');
SET @s3 := IF(@c3=0,'ALTER TABLE stock_log ADD COLUMN ref_id BIGINT NULL COMMENT ''溯源（采购入库/撤回=shopping_item.id）'' AFTER note','SELECT 1');
PREPARE p3 FROM @s3; EXECUTE p3; DEALLOCATE PREPARE p3;

-- 4) 索引：按溯源查（撤回入库）
SET @k1 := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='stock_log' AND INDEX_NAME='idx_stock_log_ref');
SET @sk := IF(@k1=0,'ALTER TABLE stock_log ADD KEY idx_stock_log_ref (ref_id)','SELECT 1');
PREPARE pk FROM @sk; EXECUTE pk; DEALLOCATE PREPARE pk;
