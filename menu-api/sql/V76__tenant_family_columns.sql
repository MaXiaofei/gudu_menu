-- ============================================================
-- V76: 家庭域表加 family_id（multi-tenant-family D.2）
-- 1) 下列家庭域表加 family_id BIGINT NULL + 索引（V77 回填、V78 改 NOT NULL）；
-- 2) dish 加 family_id（NULL = 平台公共菜谱，永久可空）+ source_dish_id（复制来源）；
-- 3) member（= 成员档案）加 account_id + UNIQUE(family_id, account_id)；
-- 4) ingredient_stock 唯一键 (ingredient_id) → (family_id, ingredient_id)：每个家庭各自一份余量。
-- 幂等：information_schema 判断（表不存在则跳过），可重复执行。
-- 回滚：sql/rollback/V75-V79_multi_tenant_family.sql
-- ============================================================
-- member
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'member' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'member' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE member ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'member' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'member' AND s.INDEX_NAME = 'idx_member_family'));
SET @s := IF(@c > 0, 'ALTER TABLE member ADD INDEX idx_member_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'menu' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE menu ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'menu' AND s.INDEX_NAME = 'idx_menu_family'));
SET @s := IF(@c > 0, 'ALTER TABLE menu ADD INDEX idx_menu_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_dish
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu_dish' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'menu_dish' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE menu_dish ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu_dish' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'menu_dish' AND s.INDEX_NAME = 'idx_menu_dish_family'));
SET @s := IF(@c > 0, 'ALTER TABLE menu_dish ADD INDEX idx_menu_dish_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_prep_status
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu_prep_status' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'menu_prep_status' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE menu_prep_status ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu_prep_status' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'menu_prep_status' AND s.INDEX_NAME = 'idx_menu_prep_status_family'));
SET @s := IF(@c > 0, 'ALTER TABLE menu_prep_status ADD INDEX idx_menu_prep_status_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_activity
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu_activity' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'menu_activity' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE menu_activity ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu_activity' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'menu_activity' AND s.INDEX_NAME = 'idx_menu_activity_family'));
SET @s := IF(@c > 0, 'ALTER TABLE menu_activity ADD INDEX idx_menu_activity_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_invite
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu_invite' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'menu_invite' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE menu_invite ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu_invite' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'menu_invite' AND s.INDEX_NAME = 'idx_menu_invite_family'));
SET @s := IF(@c > 0, 'ALTER TABLE menu_invite ADD INDEX idx_menu_invite_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_join
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu_join' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'menu_join' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE menu_join ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu_join' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'menu_join' AND s.INDEX_NAME = 'idx_menu_join_family'));
SET @s := IF(@c > 0, 'ALTER TABLE menu_join ADD INDEX idx_menu_join_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_cook_usage
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu_cook_usage' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'menu_cook_usage' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE menu_cook_usage ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu_cook_usage' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'menu_cook_usage' AND s.INDEX_NAME = 'idx_menu_cook_usage_family'));
SET @s := IF(@c > 0, 'ALTER TABLE menu_cook_usage ADD INDEX idx_menu_cook_usage_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_template
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu_template' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'menu_template' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE menu_template ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'menu_template' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'menu_template' AND s.INDEX_NAME = 'idx_menu_template_family'));
SET @s := IF(@c > 0, 'ALTER TABLE menu_template ADD INDEX idx_menu_template_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- ingredient_stock
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'ingredient_stock' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'ingredient_stock' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE ingredient_stock ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'ingredient_stock' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'ingredient_stock' AND s.INDEX_NAME = 'idx_ingredient_stock_family'));
SET @s := IF(@c > 0, 'ALTER TABLE ingredient_stock ADD INDEX idx_ingredient_stock_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- pantry
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'pantry' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'pantry' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE pantry ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'pantry' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'pantry' AND s.INDEX_NAME = 'idx_pantry_family'));
SET @s := IF(@c > 0, 'ALTER TABLE pantry ADD INDEX idx_pantry_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- pantry_change_log
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'pantry_change_log' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'pantry_change_log' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE pantry_change_log ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'pantry_change_log' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'pantry_change_log' AND s.INDEX_NAME = 'idx_pantry_change_log_family'));
SET @s := IF(@c > 0, 'ALTER TABLE pantry_change_log ADD INDEX idx_pantry_change_log_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- stock_log
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'stock_log' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'stock_log' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE stock_log ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'stock_log' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'stock_log' AND s.INDEX_NAME = 'idx_stock_log_family'));
SET @s := IF(@c > 0, 'ALTER TABLE stock_log ADD INDEX idx_stock_log_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- shopping_list
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'shopping_list' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'shopping_list' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE shopping_list ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'shopping_list' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'shopping_list' AND s.INDEX_NAME = 'idx_shopping_list_family'));
SET @s := IF(@c > 0, 'ALTER TABLE shopping_list ADD INDEX idx_shopping_list_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- shopping_item
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'shopping_item' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'shopping_item' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE shopping_item ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'shopping_item' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'shopping_item' AND s.INDEX_NAME = 'idx_shopping_item_family'));
SET @s := IF(@c > 0, 'ALTER TABLE shopping_item ADD INDEX idx_shopping_item_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- cooking_record
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'cooking_record' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'cooking_record' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE cooking_record ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'cooking_record' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'cooking_record' AND s.INDEX_NAME = 'idx_cooking_record_family'));
SET @s := IF(@c > 0, 'ALTER TABLE cooking_record ADD INDEX idx_cooking_record_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- favorite
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'favorite' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'favorite' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE favorite ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'favorite' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'favorite' AND s.INDEX_NAME = 'idx_favorite_family'));
SET @s := IF(@c > 0, 'ALTER TABLE favorite ADD INDEX idx_favorite_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- daily_log
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'daily_log' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'daily_log' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE daily_log ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'daily_log' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'daily_log' AND s.INDEX_NAME = 'idx_daily_log_family'));
SET @s := IF(@c > 0, 'ALTER TABLE daily_log ADD INDEX idx_daily_log_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- daily_log_item
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'daily_log_item' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'daily_log_item' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE daily_log_item ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'daily_log_item' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'daily_log_item' AND s.INDEX_NAME = 'idx_daily_log_item_family'));
SET @s := IF(@c > 0, 'ALTER TABLE daily_log_item ADD INDEX idx_daily_log_item_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- review
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'review' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'review' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE review ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'review' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'review' AND s.INDEX_NAME = 'idx_review_family'));
SET @s := IF(@c > 0, 'ALTER TABLE review ADD INDEX idx_review_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- review_score
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'review_score' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'review_score' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE review_score ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'review_score' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'review_score' AND s.INDEX_NAME = 'idx_review_score_family'));
SET @s := IF(@c > 0, 'ALTER TABLE review_score ADD INDEX idx_review_score_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- dish_draft
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'dish_draft' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'dish_draft' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE dish_draft ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'dish_draft' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'dish_draft' AND s.INDEX_NAME = 'idx_dish_draft_family'));
SET @s := IF(@c > 0, 'ALTER TABLE dish_draft ADD INDEX idx_dish_draft_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- dish_history
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'dish_history' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'dish_history' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE dish_history ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'dish_history' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'dish_history' AND s.INDEX_NAME = 'idx_dish_history_family'));
SET @s := IF(@c > 0, 'ALTER TABLE dish_history ADD INDEX idx_dish_history_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- meal_plan
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'meal_plan' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'meal_plan' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'meal_plan' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'meal_plan' AND s.INDEX_NAME = 'idx_meal_plan_family'));
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan ADD INDEX idx_meal_plan_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- meal_plan_item
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'meal_plan_item' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'meal_plan_item' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_item ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'meal_plan_item' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'meal_plan_item' AND s.INDEX_NAME = 'idx_meal_plan_item_family'));
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_item ADD INDEX idx_meal_plan_item_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- meal_plan_draft
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'meal_plan_draft' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'meal_plan_draft' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_draft ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'meal_plan_draft' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'meal_plan_draft' AND s.INDEX_NAME = 'idx_meal_plan_draft_family'));
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_draft ADD INDEX idx_meal_plan_draft_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- meal_plan_slot
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'meal_plan_slot' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'meal_plan_slot' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_slot ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'meal_plan_slot' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'meal_plan_slot' AND s.INDEX_NAME = 'idx_meal_plan_slot_family'));
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_slot ADD INDEX idx_meal_plan_slot_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- notification
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'notification' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'notification' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE notification ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭（租户，multi-tenant-family）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'notification' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'notification' AND s.INDEX_NAME = 'idx_notification_family'));
SET @s := IF(@c > 0, 'ALTER TABLE notification ADD INDEX idx_notification_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- dish：公共 / 本家私有
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'dish' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'dish' AND c.COLUMN_NAME = 'family_id'));
SET @s := IF(@c > 0, 'ALTER TABLE dish ADD COLUMN family_id BIGINT NULL COMMENT ''所属家庭：NULL=平台公共菜谱，非空=家庭私有''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'dish' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'dish' AND c.COLUMN_NAME = 'source_dish_id'));
SET @s := IF(@c > 0, 'ALTER TABLE dish ADD COLUMN source_dish_id BIGINT NULL COMMENT ''复制到我家的来源菜谱 id''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'dish' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'dish' AND s.INDEX_NAME = 'idx_dish_family'));
SET @s := IF(@c > 0, 'ALTER TABLE dish ADD INDEX idx_dish_family (family_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- member：成员档案关联账号（同一家庭内唯一；NULL = 无账号档案，可多条）
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'member' AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c WHERE c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = 'member' AND c.COLUMN_NAME = 'account_id'));
SET @s := IF(@c > 0, 'ALTER TABLE member ADD COLUMN account_id BIGINT NULL COMMENT ''关联账号 account.id（NULL=无账号成员档案）''', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'member' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'member' AND s.INDEX_NAME = 'uk_member_family_account'));
SET @s := IF(@c > 0, 'ALTER TABLE member ADD UNIQUE INDEX uk_member_family_account (family_id, account_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'member' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'member' AND s.INDEX_NAME = 'idx_member_account'));
SET @s := IF(@c > 0, 'ALTER TABLE member ADD INDEX idx_member_account (account_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ingredient_stock：每个家庭每种食材一行
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'ingredient_stock' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'ingredient_stock' AND s.INDEX_NAME = 'uk_stock_family_ingredient'));
SET @s := IF(@c > 0, 'ALTER TABLE ingredient_stock ADD UNIQUE INDEX uk_stock_family_ingredient (family_id, ingredient_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient_stock' AND INDEX_NAME = 'uk_stock_ingredient');
SET @s := IF(@c > 0, 'ALTER TABLE ingredient_stock DROP INDEX uk_stock_ingredient', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
