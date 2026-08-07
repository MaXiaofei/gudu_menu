-- ============================================================
-- V40 食集详情菜行：menu_dish 加 note（该菜在食集中的备注，可改可删）
-- 背景：menu-detail-cai.html 原型要求菜行带备注（如「宝宝那份少盐」），
--       备注为空时前端显示「加备注/忌口…」占位。
-- 幂等：ALTER 用 information_schema 判存在（照 V36 范式）
-- ============================================================

-- menu_dish 加 note 列（255 足够日常备注）
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='menu_dish' AND COLUMN_NAME='note');
SET @s1 := IF(@c1=0,'ALTER TABLE menu_dish ADD COLUMN note VARCHAR(255) NULL COMMENT ''该菜在食集中的备注''','SELECT 1');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;
