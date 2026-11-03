SET NAMES utf8mb4;
-- 回滚 V101（公共菜谱下架）。先确认代码已回滚到不读 offline_at 的版本再执行。
-- 回滚前如需保留下架名单：SELECT id, name, offline_at, offline_reason FROM dish WHERE offline_at IS NOT NULL;
ALTER TABLE dish DROP INDEX idx_dish_offline;
ALTER TABLE dish DROP COLUMN offline_by, DROP COLUMN offline_reason, DROP COLUMN offline_at;
