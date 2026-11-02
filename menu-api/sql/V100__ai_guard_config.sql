SET NAMES utf8mb4;
-- ============================================================
-- V100: AI 用量与额度（ai-guardrails）：全站每日花费上限 + 各类每账号每日次数上限，后台可改。
-- ai_guard_config：键值表，只存运营改过的值；没有的键取应用配置默认值
--   （预算 20 元、提醒 80%、拍照 20、餐程 10、咕嘟 50、其他 AI 50）。
--   键：budget.daily_yuan / budget.warn_percent / limit.vision / limit.mealplan / limit.agent / limit.other
-- ai_guard_config_log：每次修改留痕（操作人、时间、新旧值）。
-- 幂等：CREATE TABLE IF NOT EXISTS，不种子数据。
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_guard_config (
  config_key       VARCHAR(64)  PRIMARY KEY COMMENT '配置键，如 budget.daily_yuan',
  config_value     VARCHAR(64)  NOT NULL COMMENT '配置值（数字文本）',
  updated_by       BIGINT       NULL COMMENT '最后操作人 account.id',
  updated_by_name  VARCHAR(64)  NULL COMMENT '最后操作人昵称快照',
  updated_at       DATETIME     NULL
) COMMENT='AI 额度配置（运营可改）';

CREATE TABLE IF NOT EXISTS ai_guard_config_log (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key     VARCHAR(64)  NOT NULL,
  old_value      VARCHAR(64)  NULL COMMENT '修改前的生效值（含默认值）',
  new_value      VARCHAR(64)  NOT NULL,
  operator_id    BIGINT       NOT NULL,
  operator_name  VARCHAR(64)  NULL,
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ai_guard_log_time (create_time)
) COMMENT='AI 额度配置修改日志';
