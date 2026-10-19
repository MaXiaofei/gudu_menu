-- ============================================================
-- V63: 迁移登记表（2026-10-16，security-hardening S12）
--
-- 背景：deploy.sh 过去每次部署都重跑 V42+ 全部 SQL，非幂等脚本（V61 DELETE、
--   V62 重建「饭」标签）会清掉用户数据、导致字典 id 漂移。
-- 方案：deploy.sh 以「文件名」为主键登记已执行迁移 + sha256 校验和：
--   - 未登记 → 执行并登记；已登记且 checksum 一致 → 跳过；checksum 变化 → 中止部署。
--   - 按文件名而非版本号登记：仓库存在同号文件（V43/V45/V50 各两份），互不冲突。
-- 幂等：CREATE TABLE IF NOT EXISTS。
-- 正式迁移工具（Flyway）在 production-ops-baseline 落地后替代本表。
-- ============================================================
CREATE TABLE IF NOT EXISTS schema_migration_log (
  filename   VARCHAR(255) NOT NULL PRIMARY KEY COMMENT '迁移文件名，如 V63__schema_migration_log.sql',
  checksum   CHAR(64)     NOT NULL COMMENT '文件内容 sha256（hex）',
  applied_at DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '登记/执行时间'
) COMMENT '部署迁移登记（deploy.sh 维护）';
