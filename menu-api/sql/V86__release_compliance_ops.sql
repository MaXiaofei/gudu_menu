-- ============================================================
-- V86: 发布合规 · 同意记录与后台运营配套（mini-release-compliance B.1 / F.1–F.4）
-- consent_record         协议同意 / 撤回记录（按账号；账号合并时改指主账号）
-- account_ban            账号封禁（一行 = 当前封禁中；解封删行，历史见 admin_operation_log）
-- account_deactivation   账号注销处理记录（匿名化各步骤结果、失败重试、15 个工作日时限）
-- content_text_violation 文本内容安全违规记录（昵称 / 反馈 / AI 对话 / 社区等，只存摘录）
-- admin_operation_log    运营后台操作日志（封禁、解封、违规复核、注销重试）
-- 全部为全局表（不按家庭过滤）。幂等：CREATE TABLE IF NOT EXISTS。
-- ============================================================
CREATE TABLE IF NOT EXISTS consent_record (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  account_id  BIGINT       NOT NULL COMMENT 'account.id',
  doc_type    VARCHAR(16)  NOT NULL COMMENT 'TERMS / PRIVACY / HEALTH / MINOR',
  doc_version VARCHAR(32)  NOT NULL COMMENT '协议版本（LEGAL_VERSION，如 2026-10-26）',
  granted     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 同意 / 0 撤回',
  channel     VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN' COMMENT 'MP_WEIXIN / APP / H5 / UNKNOWN',
  client_ip   VARCHAR(64)  NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_consent_account (account_id, created_at)
) COMMENT '协议同意记录';

CREATE TABLE IF NOT EXISTS account_ban (
  account_id BIGINT       PRIMARY KEY COMMENT 'account.id',
  reason     VARCHAR(200) NOT NULL,
  banned_by  BIGINT       NOT NULL COMMENT '操作人 account.id',
  banned_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '账号封禁（当前生效）';

CREATE TABLE IF NOT EXISTS account_deactivation (
  id           BIGINT        PRIMARY KEY AUTO_INCREMENT,
  account_id   BIGINT        NOT NULL,
  requested_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '用户发起注销时间',
  status       VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / DONE / FAILED',
  steps        VARCHAR(2000) NULL COMMENT '各匿名化步骤结果 JSON [{name,ok,detail}]',
  attempts     INT           NOT NULL DEFAULT 0,
  last_error   VARCHAR(500)  NULL,
  completed_at DATETIME      NULL,
  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_deactivation_account (account_id),
  KEY idx_deactivation_status (status, requested_at)
) COMMENT '账号注销处理记录';

CREATE TABLE IF NOT EXISTS content_text_violation (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  account_id    BIGINT       NULL COMMENT '提交人 account.id',
  purpose       VARCHAR(16)  NOT NULL COMMENT 'NICKNAME / FEEDBACK / AGENT_INPUT / AGENT_OUTPUT / POST / COMMENT …',
  suggest       VARCHAR(16)  NOT NULL COMMENT 'REVIEW / RISKY',
  label         INT          NOT NULL DEFAULT 0,
  source        VARCHAR(16)  NOT NULL COMMENT 'RULE / WECHAT / RULE_FALLBACK',
  excerpt       VARCHAR(200) NULL COMMENT '内容摘录（前 100 字，供复核）',
  review_status VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / CONFIRMED',
  reviewed_by   BIGINT       NULL,
  reviewed_at   DATETIME     NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_text_violation_created (created_at),
  KEY idx_text_violation_account (account_id)
) COMMENT '文本内容安全违规记录';

CREATE TABLE IF NOT EXISTS admin_operation_log (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  operator_id BIGINT       NOT NULL COMMENT '操作人 account.id',
  action      VARCHAR(32)  NOT NULL COMMENT 'BAN / UNBAN / RESTORE_MEDIA / CONFIRM_MEDIA / CONFIRM_TEXT / RETRY_DEACTIVATION',
  target_type VARCHAR(32)  NOT NULL COMMENT 'ACCOUNT / MEDIA_CHECK / TEXT_VIOLATION / DEACTIVATION',
  target_id   BIGINT       NOT NULL,
  reason      VARCHAR(200) NULL,
  detail      VARCHAR(500) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_oplog_created (created_at),
  KEY idx_oplog_target (target_type, target_id)
) COMMENT '运营后台操作日志';
