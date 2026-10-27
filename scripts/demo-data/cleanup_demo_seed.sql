-- 咕嘟小食单 · 清除演示数据
-- 按登记表 demo_seed_registry 逆序删除 seed_demo.sql 插入的全部行（含老公在我们家的成员关系与档案、朋友的账号与家庭），
-- 并把 demo_seed_restore 里记录的「被改过的已有行」还原；最后删除两张登记表。
-- 老公自己的账号和他自建的家庭不删、不动。可重复执行：登记表不存在时按空处理。
-- 用法：mysql --default-character-set=utf8mb4 <库名> < cleanup_demo_seed.sql
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS demo_seed_registry (
  seq BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  table_name VARCHAR(64) NOT NULL,
  row_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_demo_registry_tbl (table_name, row_id)
) DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS demo_seed_restore (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  table_name VARCHAR(64) NOT NULL,
  row_id BIGINT NOT NULL,
  column_name VARCHAR(64) NOT NULL,
  old_value VARCHAR(64) NULL,
  new_value VARCHAR(64) NULL
) DEFAULT CHARSET=utf8mb4;

START TRANSACTION;

-- 1) 还原被改过的已有行（仅当当前值仍是脚本写入的值时才还原，避免覆盖之后的真实修改）
UPDATE account a JOIN demo_seed_restore r
  ON r.table_name = 'account' AND r.row_id = a.id AND r.column_name = 'created_at'
  SET a.created_at = r.old_value WHERE a.created_at = r.new_value;
UPDATE account a JOIN demo_seed_restore r
  ON r.table_name = 'account' AND r.row_id = a.id AND r.column_name = 'last_family_id'
  SET a.last_family_id = IF(r.old_value IS NULL, NULL, CAST(r.old_value AS UNSIGNED))
  WHERE a.last_family_id = CAST(r.new_value AS UNSIGNED);
UPDATE family f JOIN demo_seed_restore r
  ON r.table_name = 'family' AND r.row_id = f.id AND r.column_name = 'created_at'
  SET f.created_at = r.old_value WHERE f.created_at = r.new_value;
UPDATE family_membership m JOIN demo_seed_restore r
  ON r.table_name = 'family_membership' AND r.row_id = m.id AND r.column_name = 'joined_at'
  SET m.joined_at = r.old_value WHERE m.joined_at = r.new_value;
UPDATE member m JOIN demo_seed_restore r
  ON r.table_name = 'member' AND r.row_id = m.id AND r.column_name = 'create_time'
  SET m.create_time = r.old_value WHERE m.create_time = r.new_value;

-- 2) 按依赖逆序删除登记过的行（子表在前，账号 / 家庭最后）
DELETE t FROM notification t JOIN demo_seed_registry r ON r.table_name = 'notification' AND r.row_id = t.id;
DELETE t FROM review_score t JOIN demo_seed_registry r ON r.table_name = 'review_score' AND r.row_id = t.id;
DELETE t FROM review t JOIN demo_seed_registry r ON r.table_name = 'review' AND r.row_id = t.id;
DELETE t FROM daily_log_item t JOIN demo_seed_registry r ON r.table_name = 'daily_log_item' AND r.row_id = t.id;
DELETE t FROM daily_log t JOIN demo_seed_registry r ON r.table_name = 'daily_log' AND r.row_id = t.id;
DELETE t FROM stock_log t JOIN demo_seed_registry r ON r.table_name = 'stock_log' AND r.row_id = t.id;
DELETE t FROM ingredient_stock t JOIN demo_seed_registry r ON r.table_name = 'ingredient_stock' AND r.row_id = t.id;
DELETE t FROM shopping_item t JOIN demo_seed_registry r ON r.table_name = 'shopping_item' AND r.row_id = t.id;
DELETE t FROM shopping_list t JOIN demo_seed_registry r ON r.table_name = 'shopping_list' AND r.row_id = t.id;
DELETE t FROM meal_plan_slot t JOIN demo_seed_registry r ON r.table_name = 'meal_plan_slot' AND r.row_id = t.id;
DELETE t FROM meal_plan_draft t JOIN demo_seed_registry r ON r.table_name = 'meal_plan_draft' AND r.row_id = t.id;
DELETE t FROM favorite t JOIN demo_seed_registry r ON r.table_name = 'favorite' AND r.row_id = t.id;
DELETE t FROM cooking_record t JOIN demo_seed_registry r ON r.table_name = 'cooking_record' AND r.row_id = t.id;
DELETE t FROM menu_cook_usage t JOIN demo_seed_registry r ON r.table_name = 'menu_cook_usage' AND r.row_id = t.id;
DELETE t FROM menu_prep_status t JOIN demo_seed_registry r ON r.table_name = 'menu_prep_status' AND r.row_id = t.id;
DELETE t FROM menu_activity t JOIN demo_seed_registry r ON r.table_name = 'menu_activity' AND r.row_id = t.id;
DELETE t FROM menu_join t JOIN demo_seed_registry r ON r.table_name = 'menu_join' AND r.row_id = t.id;
DELETE t FROM menu_invite t JOIN demo_seed_registry r ON r.table_name = 'menu_invite' AND r.row_id = t.id;
DELETE t FROM menu_dish t JOIN demo_seed_registry r ON r.table_name = 'menu_dish' AND r.row_id = t.id;
DELETE t FROM menu t JOIN demo_seed_registry r ON r.table_name = 'menu' AND r.row_id = t.id;
DELETE t FROM consent_record t JOIN demo_seed_registry r ON r.table_name = 'consent_record' AND r.row_id = t.id;
DELETE t FROM member t JOIN demo_seed_registry r ON r.table_name = 'member' AND r.row_id = t.id;
DELETE t FROM family_membership t JOIN demo_seed_registry r ON r.table_name = 'family_membership' AND r.row_id = t.id;
DELETE t FROM family t JOIN demo_seed_registry r ON r.table_name = 'family' AND r.row_id = t.id;
DELETE t FROM account t JOIN demo_seed_registry r ON r.table_name = 'account' AND r.row_id = t.id;

-- 3) 自检：登记过的行是否还有残留（应全部为 0）
SELECT r.table_name, COUNT(*) AS leftover
FROM demo_seed_registry r
JOIN (
  SELECT 'notification' tn, id FROM notification UNION ALL SELECT 'review_score', id FROM review_score
  UNION ALL SELECT 'review', id FROM review UNION ALL SELECT 'daily_log_item', id FROM daily_log_item
  UNION ALL SELECT 'daily_log', id FROM daily_log UNION ALL SELECT 'stock_log', id FROM stock_log
  UNION ALL SELECT 'ingredient_stock', id FROM ingredient_stock UNION ALL SELECT 'shopping_item', id FROM shopping_item
  UNION ALL SELECT 'shopping_list', id FROM shopping_list UNION ALL SELECT 'meal_plan_slot', id FROM meal_plan_slot
  UNION ALL SELECT 'meal_plan_draft', id FROM meal_plan_draft UNION ALL SELECT 'favorite', id FROM favorite
  UNION ALL SELECT 'cooking_record', id FROM cooking_record UNION ALL SELECT 'menu_cook_usage', id FROM menu_cook_usage
  UNION ALL SELECT 'menu_prep_status', id FROM menu_prep_status UNION ALL SELECT 'menu_activity', id FROM menu_activity
  UNION ALL SELECT 'menu_join', id FROM menu_join UNION ALL SELECT 'menu_invite', id FROM menu_invite
  UNION ALL SELECT 'menu_dish', id FROM menu_dish UNION ALL SELECT 'menu', id FROM menu
  UNION ALL SELECT 'consent_record', id FROM consent_record UNION ALL SELECT 'member', id FROM member
  UNION ALL SELECT 'family_membership', id FROM family_membership UNION ALL SELECT 'family', id FROM family
  UNION ALL SELECT 'account', id FROM account
) x ON x.tn = r.table_name AND x.id = r.row_id
GROUP BY r.table_name;

COMMIT;

-- 4) 删除登记表（DDL，事务外）。上面自检若有非 0 行，先排查再删表。
DROP TABLE IF EXISTS demo_seed_registry;
DROP TABLE IF EXISTS demo_seed_restore;
