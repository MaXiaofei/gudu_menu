-- V43: 食集统一评价（review 支持 menu 维度）
-- 背景：docs/menu-review-design.md —— 食集整体评价复用单菜四维度；review 表 dish_id 改可空 + 加 menu_id（二选一）
-- 幂等：ALTER/ADD KEY 用 information_schema 判存在（照 V36 范式）

-- 1) review 加 menu_id（食集评价）
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='review' AND COLUMN_NAME='menu_id');
SET @s1 := IF(@c1=0,'ALTER TABLE review ADD COLUMN menu_id BIGINT NULL AFTER dish_id','SELECT 1');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;

-- 2) dish_id 改可空（食集评价行不填）
ALTER TABLE review MODIFY dish_id BIGINT NULL COMMENT '菜品 id（与 menu_id 二选一；食集评价为 NULL）';

-- 3) menu_id 索引（统一评价页/我的评价查询）
SET @k1 := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='review' AND INDEX_NAME='idx_review_menu');
SET @sk := IF(@k1=0,'ALTER TABLE review ADD KEY idx_review_menu (menu_id)','SELECT 1');
PREPARE pk FROM @sk; EXECUTE pk; DEALLOCATE PREPARE pk;
