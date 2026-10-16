-- ============================================================
-- V65: cooking_record 加 event_id（feature-gaps-closure G1）
-- 做菜改事件驱动：DishCookedEvent 由 cookbook 消费写记录，event_id = 事件 id（同一次做菜多道菜共享），
-- 作幂等键并供发布方回查本次写入的记录 id。存量数据为 NULL。
-- 幂等：information_schema 判断。
-- ============================================================
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooking_record' AND COLUMN_NAME = 'event_id');
SET @s := IF(@c = 0,
  'ALTER TABLE cooking_record ADD COLUMN event_id VARCHAR(36) NULL COMMENT ''DishCookedEvent.eventId（幂等键）''',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooking_record' AND INDEX_NAME = 'idx_cooking_event');
SET @s := IF(@c = 0, 'ALTER TABLE cooking_record ADD INDEX idx_cooking_event (event_id)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 最近做过 / 你常做：按成员 + 时间聚合
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cooking_record' AND INDEX_NAME = 'idx_cooking_member_time');
SET @s := IF(@c = 0, 'ALTER TABLE cooking_record ADD INDEX idx_cooking_member_time (member_id, cooked_at)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
