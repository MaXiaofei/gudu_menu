SET NAMES utf8mb4;
-- ============================================================
-- V101: 公共菜谱下架 / 重新上架
-- 取舍：dish 加「下架时间」而不是 status 枚举列——
--   offline_at IS NULL = 上架（默认，存量数据全部保持上架，本迁移不改任何存量行）；
--   offline_at 非空   = 已下架，同时记录下架原因与操作人。
-- 只有公共菜谱（family_id IS NULL）允许下架；家庭私有菜谱此三列恒为 NULL。
-- 规则：下架后「新入口」隐藏（搜索、推荐、语义检索、餐程候选、AI 咕嘟推荐、加入食集、照着做复制），
-- 家庭已加入的食集 / 餐程 / 做菜记录 / 收藏 / 已复制的家庭菜谱照常可用；菜谱详情仍可读并带 offline 标记。
-- 幂等：用 information_schema 判断后再加列 / 索引。
-- ============================================================
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND COLUMN_NAME = 'offline_at');
SET @sql := IF(@has_col = 0,
  'ALTER TABLE dish ADD COLUMN offline_at DATETIME NULL COMMENT ''下架时间；NULL=上架（仅公共菜谱可下架）'', ADD COLUMN offline_reason VARCHAR(200) NULL COMMENT ''下架原因（运营填写）'', ADD COLUMN offline_by BIGINT NULL COMMENT ''下架操作人 account.id''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dish' AND INDEX_NAME = 'idx_dish_offline');
SET @sql := IF(@has_idx = 0, 'ALTER TABLE dish ADD INDEX idx_dish_offline (offline_at)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
