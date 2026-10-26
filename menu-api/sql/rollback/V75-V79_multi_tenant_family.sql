-- ============================================================
-- 回滚：multi-tenant-family V75–V79（D.5）
-- 前提：已回退到 V75 之前的应用版本（旧代码不读 account/family，member 旧凭证列 phone/openid/password_hash/is_admin 未删）。
-- 注意：
--   1) 迁移后新注册的账号 / 新建的家庭及其数据会被一并丢弃家庭归属（家庭域数据合并回单一全局视图）；
--   2) 迁移后经 /account/merge-phone 合并的账号：member 旧列未同步，回滚后微信登录会重新落到旧 member 行；
--   3) 先在 staging 用生产快照演练，执行前全量备份。
-- 幂等：information_schema 判断。
-- ============================================================
-- ingredient_stock 唯一键还原（若多家庭已有同一食材，需先人工去重：保留 family_id=1 的行）
DELETE s FROM ingredient_stock s JOIN ingredient_stock k ON k.ingredient_id = s.ingredient_id AND k.family_id = 1 AND s.family_id <> 1;
SET @c := (SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = 'ingredient_stock' AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS s WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'ingredient_stock' AND s.INDEX_NAME = 'uk_stock_ingredient'));
SET @s := IF(@c > 0, 'ALTER TABLE ingredient_stock ADD UNIQUE INDEX uk_stock_ingredient (ingredient_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient_stock' AND INDEX_NAME = 'uk_stock_family_ingredient');
SET @s := IF(@c > 0, 'ALTER TABLE ingredient_stock DROP INDEX uk_stock_family_ingredient', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- member
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND INDEX_NAME = 'idx_member_family');
SET @s := IF(@c > 0, 'ALTER TABLE member DROP INDEX idx_member_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu' AND INDEX_NAME = 'idx_menu_family');
SET @s := IF(@c > 0, 'ALTER TABLE menu DROP INDEX idx_menu_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_dish
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_dish' AND INDEX_NAME = 'idx_menu_dish_family');
SET @s := IF(@c > 0, 'ALTER TABLE menu_dish DROP INDEX idx_menu_dish_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_prep_status
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_prep_status' AND INDEX_NAME = 'idx_menu_prep_status_family');
SET @s := IF(@c > 0, 'ALTER TABLE menu_prep_status DROP INDEX idx_menu_prep_status_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_activity
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_activity' AND INDEX_NAME = 'idx_menu_activity_family');
SET @s := IF(@c > 0, 'ALTER TABLE menu_activity DROP INDEX idx_menu_activity_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_invite
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_invite' AND INDEX_NAME = 'idx_menu_invite_family');
SET @s := IF(@c > 0, 'ALTER TABLE menu_invite DROP INDEX idx_menu_invite_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_join
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_join' AND INDEX_NAME = 'idx_menu_join_family');
SET @s := IF(@c > 0, 'ALTER TABLE menu_join DROP INDEX idx_menu_join_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_cook_usage
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_cook_usage' AND INDEX_NAME = 'idx_menu_cook_usage_family');
SET @s := IF(@c > 0, 'ALTER TABLE menu_cook_usage DROP INDEX idx_menu_cook_usage_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- menu_template
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_template' AND INDEX_NAME = 'idx_menu_template_family');
SET @s := IF(@c > 0, 'ALTER TABLE menu_template DROP INDEX idx_menu_template_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- ingredient_stock
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient_stock' AND INDEX_NAME = 'idx_ingredient_stock_family');
SET @s := IF(@c > 0, 'ALTER TABLE ingredient_stock DROP INDEX idx_ingredient_stock_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- pantry
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pantry' AND INDEX_NAME = 'idx_pantry_family');
SET @s := IF(@c > 0, 'ALTER TABLE pantry DROP INDEX idx_pantry_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- pantry_change_log
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pantry_change_log' AND INDEX_NAME = 'idx_pantry_change_log_family');
SET @s := IF(@c > 0, 'ALTER TABLE pantry_change_log DROP INDEX idx_pantry_change_log_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- stock_log
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_log' AND INDEX_NAME = 'idx_stock_log_family');
SET @s := IF(@c > 0, 'ALTER TABLE stock_log DROP INDEX idx_stock_log_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- shopping_list
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_list' AND INDEX_NAME = 'idx_shopping_list_family');
SET @s := IF(@c > 0, 'ALTER TABLE shopping_list DROP INDEX idx_shopping_list_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- shopping_item
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_item' AND INDEX_NAME = 'idx_shopping_item_family');
SET @s := IF(@c > 0, 'ALTER TABLE shopping_item DROP INDEX idx_shopping_item_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- cooking_record
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooking_record' AND INDEX_NAME = 'idx_cooking_record_family');
SET @s := IF(@c > 0, 'ALTER TABLE cooking_record DROP INDEX idx_cooking_record_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- favorite
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'favorite' AND INDEX_NAME = 'idx_favorite_family');
SET @s := IF(@c > 0, 'ALTER TABLE favorite DROP INDEX idx_favorite_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- daily_log
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_log' AND INDEX_NAME = 'idx_daily_log_family');
SET @s := IF(@c > 0, 'ALTER TABLE daily_log DROP INDEX idx_daily_log_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- daily_log_item
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_log_item' AND INDEX_NAME = 'idx_daily_log_item_family');
SET @s := IF(@c > 0, 'ALTER TABLE daily_log_item DROP INDEX idx_daily_log_item_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- review
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND INDEX_NAME = 'idx_review_family');
SET @s := IF(@c > 0, 'ALTER TABLE review DROP INDEX idx_review_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- review_score
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review_score' AND INDEX_NAME = 'idx_review_score_family');
SET @s := IF(@c > 0, 'ALTER TABLE review_score DROP INDEX idx_review_score_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- dish_draft
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_draft' AND INDEX_NAME = 'idx_dish_draft_family');
SET @s := IF(@c > 0, 'ALTER TABLE dish_draft DROP INDEX idx_dish_draft_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- dish_history
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_history' AND INDEX_NAME = 'idx_dish_history_family');
SET @s := IF(@c > 0, 'ALTER TABLE dish_history DROP INDEX idx_dish_history_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- meal_plan
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan' AND INDEX_NAME = 'idx_meal_plan_family');
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan DROP INDEX idx_meal_plan_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- meal_plan_item
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_item' AND INDEX_NAME = 'idx_meal_plan_item_family');
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_item DROP INDEX idx_meal_plan_item_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- meal_plan_draft
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_draft' AND INDEX_NAME = 'idx_meal_plan_draft_family');
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_draft DROP INDEX idx_meal_plan_draft_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- meal_plan_slot
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_slot' AND INDEX_NAME = 'idx_meal_plan_slot_family');
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_slot DROP INDEX idx_meal_plan_slot_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- notification
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND INDEX_NAME = 'idx_notification_family');
SET @s := IF(@c > 0, 'ALTER TABLE notification DROP INDEX idx_notification_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND INDEX_NAME = 'uk_member_family_account');
SET @s := IF(@c > 0, 'ALTER TABLE member DROP INDEX uk_member_family_account', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND INDEX_NAME = 'idx_member_account');
SET @s := IF(@c > 0, 'ALTER TABLE member DROP INDEX idx_member_account', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND COLUMN_NAME = 'account_id');
SET @s := IF(@c > 0, 'ALTER TABLE member DROP COLUMN account_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE member DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE menu DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_dish' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE menu_dish DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_prep_status' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE menu_prep_status DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_activity' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE menu_activity DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_invite' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE menu_invite DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_join' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE menu_join DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_cook_usage' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE menu_cook_usage DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_template' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE menu_template DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ingredient_stock' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE ingredient_stock DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pantry' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE pantry DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pantry_change_log' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE pantry_change_log DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_log' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE stock_log DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_list' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE shopping_list DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_item' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE shopping_item DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooking_record' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE cooking_record DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'favorite' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE favorite DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_log' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE daily_log DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'daily_log_item' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE daily_log_item DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE review DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review_score' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE review_score DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_draft' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE dish_draft DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish_history' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE dish_history DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_item' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_item DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_draft' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_draft DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meal_plan_slot' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE meal_plan_slot DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE notification DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- dish：家庭私有菜谱回滚后变为公共可见（旧版本无私有概念）
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND INDEX_NAME = 'idx_dish_family');
SET @s := IF(@c > 0, 'ALTER TABLE dish DROP INDEX idx_dish_family', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'family_id');
SET @s := IF(@c > 0, 'ALTER TABLE dish DROP COLUMN family_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'source_dish_id');
SET @s := IF(@c > 0, 'ALTER TABLE dish DROP COLUMN source_dish_id', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 新表
DROP TABLE IF EXISTS account_merge_log;
DROP TABLE IF EXISTS family_invitation;
DROP TABLE IF EXISTS family_membership;
DROP TABLE IF EXISTS family;
DROP TABLE IF EXISTS account;

-- 迁移登记（deploy.sh 按文件名登记；回滚后需删除登记，重新部署时才会再执行）
DELETE FROM schema_migration_log WHERE filename IN (
  'V75__account_family.sql', 'V76__tenant_family_columns.sql', 'V77__tenant_data_migration.sql',
  'V78__tenant_family_not_null.sql', 'V79__account_merge_log.sql');
