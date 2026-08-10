-- V48: dish_draft 加菜系（cuisine_ids，写菜谱「菜系（可选）」单选，§16.2）
-- 幂等：ALTER 用 information_schema 判存在（照 V36/V44 范式）

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dish_draft' AND COLUMN_NAME='cuisine_ids');
SET @s1 := IF(@c1=0,
  'ALTER TABLE dish_draft ADD COLUMN cuisine_ids JSON NULL COMMENT ''菜系 dict id（单选，可空）'' AFTER tags',
  'SELECT 1');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;
