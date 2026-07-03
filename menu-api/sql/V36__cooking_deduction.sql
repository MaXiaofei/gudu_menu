-- ============================================================
-- V36 做菜扣库存链：cooking_record 加来源/份数/欠量；menu 加状态/完成时间
-- 背景：docs/redesign-audit.md §7 §8（cooking_record 无 menuId/servingFactor/source）
-- 幂等：ALTER/ADD KEY 用 information_schema 判存在（照 V35 范式）
-- ============================================================

-- 1) cooking_record 加 menu_id / serving_factor / source / memo
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cooking_record' AND COLUMN_NAME='menu_id');
SET @s1 := IF(@c1=0,'ALTER TABLE cooking_record ADD COLUMN menu_id BIGINT NULL AFTER dish_id','SELECT 1');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cooking_record' AND COLUMN_NAME='serving_factor');
SET @s2 := IF(@c2=0,'ALTER TABLE cooking_record ADD COLUMN serving_factor DECIMAL(5,2) NULL','SELECT 1');
PREPARE p2 FROM @s2; EXECUTE p2; DEALLOCATE PREPARE p2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cooking_record' AND COLUMN_NAME='source');
SET @s3 := IF(@c3=0,'ALTER TABLE cooking_record ADD COLUMN source VARCHAR(16) NULL','SELECT 1');
PREPARE p3 FROM @s3; EXECUTE p3; DEALLOCATE PREPARE p3;

SET @c4 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cooking_record' AND COLUMN_NAME='memo');
SET @s4 := IF(@c4=0,'ALTER TABLE cooking_record ADD COLUMN memo VARCHAR(1024) NULL','SELECT 1');
PREPARE p4 FROM @s4; EXECUTE p4; DEALLOCATE PREPARE p4;

-- 2) menu 加 status / finished_at
SET @c5 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='menu' AND COLUMN_NAME='status');
SET @s5 := IF(@c5=0,'ALTER TABLE menu ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT ''ACTIVE''','SELECT 1');
PREPARE p5 FROM @s5; EXECUTE p5; DEALLOCATE PREPARE p5;

SET @c6 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='menu' AND COLUMN_NAME='finished_at');
SET @s6 := IF(@c6=0,'ALTER TABLE menu ADD COLUMN finished_at DATETIME NULL','SELECT 1');
PREPARE p6 FROM @s6; EXECUTE p6; DEALLOCATE PREPARE p6;

-- 3) cooking_record 加 menu_id 索引（按食集回溯"这顿饭做了啥"）
SET @k1 := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cooking_record' AND INDEX_NAME='idx_menu');
SET @sk := IF(@k1=0,'ALTER TABLE cooking_record ADD KEY idx_menu (menu_id)','SELECT 1');
PREPARE pk FROM @sk; EXECUTE pk; DEALLOCATE PREPARE pk;
