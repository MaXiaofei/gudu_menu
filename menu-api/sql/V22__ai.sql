-- V22: AI 调用日志表（营养补全 / 菜单推荐等 AI 场景的调用审计）。
-- 每次调用 AiClient 记一条：场景、请求/响应、token 消耗、费用、provider、延迟、状态。
-- index on (scene, create_time) 便于按场景统计近期调用。
CREATE TABLE ai_call_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  scene       VARCHAR(32) NOT NULL COMMENT '场景：nutrition_fill / menu_recommend',
  member_id   BIGINT COMMENT '关联 member（可空）',
  request     TEXT COMMENT '请求 JSON',
  response    TEXT COMMENT '响应 JSON',
  tokens_in   INT DEFAULT 0 COMMENT '入 tokens',
  tokens_out  INT DEFAULT 0 COMMENT '出 tokens',
  cost        DECIMAL(8,4) DEFAULT 0 COMMENT '费用（元）',
  provider    VARCHAR(16) DEFAULT 'mock' COMMENT 'mock / glm',
  latency_ms  INT COMMENT '本次调用耗时 ms',
  status      VARCHAR(16) COMMENT 'ok / fail',
  error_msg   VARCHAR(512),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT='AI 调用日志';
CREATE INDEX idx_aicall_scene ON ai_call_log(scene, create_time);
