-- V44: shopping_list 加 name（自定义采购清单名，标题旁 ✎ 可改）
-- 背景：P2 B4 —— 自定义采购（备忘录用途）可命名，食集生成清单名为空（用来源标识展示）
-- 幂等：ALTER 用 information_schema 判存在（照 V36 范式）

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shopping_list' AND COLUMN_NAME='name');
SET @s1 := IF(@c1=0,'ALTER TABLE shopping_list ADD COLUMN name VARCHAR(64) NULL COMMENT ''清单名（自定义采购，可空）'' AFTER source_menu_id','SELECT 1');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;
