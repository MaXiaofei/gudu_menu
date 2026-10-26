-- ============================================================
-- V79: 账号合并审计（multi-tenant-family E.7 / E.8）
-- 微信账号 W 并入手机号账号 P（POST /account/merge-phone）时写一行，不可回滚但可追溯。
-- E.8：存量「同一人两个账号」不自动合并（无法证明是同一人），只在用户主动发起时合并；
--      疑似重复账号见 sql/verify/tenant_migration_check.sql 末尾查询（只读）。
-- 幂等：CREATE TABLE IF NOT EXISTS。
-- ============================================================
CREATE TABLE IF NOT EXISTS account_merge_log (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  source_id   BIGINT       NOT NULL COMMENT '被并入的账号（微信账号 W），合并后 status=MERGED',
  target_id   BIGINT       NOT NULL COMMENT '保留的账号（手机号账号 P）',
  operator_id BIGINT       NULL COMMENT '发起人 account.id（V1 = W 本人）',
  detail      VARCHAR(255) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_merge_source (source_id),
  KEY idx_merge_target (target_id)
) COMMENT '账号合并审计';
