-- ============================================================
-- V78: 家庭域表 family_id 改 NOT NULL（multi-tenant-family D.4；dish 除外——NULL 表示公共菜谱）
-- 先兜底回填（V77 之后、V78 之前若有写入），再 MODIFY。幂等：仅当列仍可空时执行。
-- ============================================================
-- member
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE member SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE member MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE menu MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_dish
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_dish' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu_dish SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_dish' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE menu_dish MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_prep_status
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_prep_status' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu_prep_status SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_prep_status' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE menu_prep_status MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_activity
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_activity' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu_activity SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_activity' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE menu_activity MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_invite
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_invite' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu_invite SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_invite' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE menu_invite MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_join
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_join' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu_join SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_join' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE menu_join MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_cook_usage
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_cook_usage' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu_cook_usage SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_cook_usage' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE menu_cook_usage MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_template
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_template' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE menu_template SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_template' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE menu_template MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- ingredient_stock
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient_stock' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE ingredient_stock SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient_stock' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE ingredient_stock MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- pantry
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pantry' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE pantry SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pantry' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE pantry MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- pantry_change_log
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pantry_change_log' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE pantry_change_log SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pantry_change_log' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE pantry_change_log MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- stock_log
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_log' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE stock_log SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_log' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE stock_log MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- shopping_list
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_list' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE shopping_list SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_list' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE shopping_list MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- shopping_item
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_item' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE shopping_item SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_item' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE shopping_item MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- cooking_record
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooking_record' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE cooking_record SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooking_record' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE cooking_record MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- favorite
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'favorite' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE favorite SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'favorite' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE favorite MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- daily_log
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_log' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE daily_log SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_log' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE daily_log MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- daily_log_item
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_log_item' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE daily_log_item SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_log_item' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE daily_log_item MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- review
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE review SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE review MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- review_score
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review_score' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE review_score SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review_score' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE review_score MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- dish_draft
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_draft' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE dish_draft SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_draft' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE dish_draft MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- dish_history
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_history' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE dish_history SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_history' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE dish_history MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- meal_plan
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE meal_plan SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- meal_plan_item
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_item' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE meal_plan_item SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_item' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_item MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- meal_plan_draft
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_draft' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE meal_plan_draft SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_draft' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_draft MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- meal_plan_slot
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_slot' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE meal_plan_slot SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_slot' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_slot MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- notification
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'UPDATE notification SET family_id = 1 WHERE family_id IS NULL', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'family_id' AND IS_NULLABLE = 'YES');
SET @s := IF(@c > 0, 'ALTER TABLE notification MODIFY COLUMN family_id BIGINT NOT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
