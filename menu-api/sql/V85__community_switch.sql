-- ============================================================
-- V85: 运行时功能开关（mini-release-compliance A.12 厨房圈紧急关停）
-- sys_switch：当前状态 + 最后操作人；sys_switch_log：每次修改留痕（操作人、时间、原因）。
-- 种子 community.enabled=1（默认开）。幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE。
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_switch (
  switch_key       VARCHAR(64)  PRIMARY KEY COMMENT '开关键，如 community.enabled',
  enabled          TINYINT      NOT NULL DEFAULT 1,
  updated_by       BIGINT       NULL COMMENT '最后操作人 member.id',
  updated_by_name  VARCHAR(64)  NULL COMMENT '最后操作人昵称快照',
  updated_at       DATETIME     NULL,
  reason           VARCHAR(200) NULL COMMENT '最后一次切换原因'
) COMMENT='运行时功能开关（紧急关停用）';

CREATE TABLE IF NOT EXISTS sys_switch_log (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  switch_key     VARCHAR(64)  NOT NULL,
  enabled        TINYINT      NOT NULL COMMENT '切换后的状态',
  operator_id    BIGINT       NOT NULL,
  operator_name  VARCHAR(64)  NULL,
  reason         VARCHAR(200) NULL,
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_sys_switch_log_key_time (switch_key, create_time)
) COMMENT='运行时功能开关修改日志';

INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('community.enabled', 1);
