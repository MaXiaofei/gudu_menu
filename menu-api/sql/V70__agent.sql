-- ============================================================
-- V70: 咕嘟 Agent（showcase-ai-community §2 / §7）
-- 会话、消息、调用链（trace / span）、评测用例（库内部分）与评测运行记录。
-- 待确认动作与撤销凭据在 Redis（5 分钟 TTL），消息里保存动作快照。
-- 幂等：CREATE TABLE IF NOT EXISTS。
-- ============================================================

CREATE TABLE IF NOT EXISTS agent_session (
  id           VARCHAR(40)  PRIMARY KEY,
  member_id    BIGINT       NOT NULL,
  title        VARCHAR(64)  NULL,
  last_message VARCHAR(255) NULL,
  cover_url    VARCHAR(512) NULL,
  created_at   DATETIME(3)  NOT NULL,
  updated_at   DATETIME(3)  NOT NULL,
  deleted      TINYINT      NOT NULL DEFAULT 0,
  KEY idx_agent_session_member (member_id, updated_at)
) COMMENT='咕嘟会话';

CREATE TABLE IF NOT EXISTS agent_message (
  id               VARCHAR(40)  PRIMARY KEY,
  session_id       VARCHAR(40)  NOT NULL,
  member_id        BIGINT       NOT NULL,
  role             VARCHAR(16)  NOT NULL COMMENT 'user / assistant',
  text             TEXT         NULL,
  tools_json       MEDIUMTEXT   NULL COMMENT '工具调用记录',
  cards_json       MEDIUMTEXT   NULL COMMENT '卡片',
  actions_json     MEDIUMTEXT   NULL COMMENT '待确认动作快照',
  trace_id         VARCHAR(40)  NULL,
  reasoning        MEDIUMTEXT   NULL COMMENT '思考内容（思考模式回传模型）',
  rating           TINYINT      NULL COMMENT '1 / -1',
  feedback_comment VARCHAR(255) NULL,
  created_at       DATETIME(3)  NOT NULL,
  KEY idx_agent_message_session (session_id, created_at)
) COMMENT='咕嘟消息';

CREATE TABLE IF NOT EXISTS agent_trace (
  id                VARCHAR(40)  PRIMARY KEY,
  session_id        VARCHAR(40)  NULL,
  message_id        VARCHAR(40)  NULL,
  member_id         BIGINT       NULL,
  first_text        VARCHAR(255) NULL COMMENT '用户首句（已脱敏）',
  status            VARCHAR(16)  NOT NULL COMMENT 'OK / DOWNVOTED / GUARDED / DEGRADED / ERROR',
  provider          VARCHAR(64)  NULL,
  model             VARCHAR(64)  NULL,
  prompt_version    VARCHAR(32)  NULL,
  degraded          TINYINT      NOT NULL DEFAULT 0,
  ms                INT          NULL,
  first_token_ms    INT          NULL,
  prompt_tokens     INT          NOT NULL DEFAULT 0,
  completion_tokens INT          NOT NULL DEFAULT 0,
  cost_fen          DECIMAL(12,4) NOT NULL DEFAULT 0,
  phone_masked      INT          NOT NULL DEFAULT 0,
  feedback          TINYINT      NULL,
  created_at        DATETIME(3)  NOT NULL,
  KEY idx_agent_trace_time (created_at),
  KEY idx_agent_trace_status (status, created_at)
) COMMENT='咕嘟调用链（一轮对话）';

CREATE TABLE IF NOT EXISTS agent_span (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  trace_id    VARCHAR(40)  NOT NULL,
  span_id     VARCHAR(16)  NOT NULL,
  parent_id   VARCHAR(16)  NULL,
  type        VARCHAR(16)  NOT NULL COMMENT 'guard / llm / tool / retrieval / action',
  name        VARCHAR(64)  NOT NULL,
  start_ms    INT          NOT NULL,
  ms          INT          NOT NULL,
  tokens      INT          NOT NULL DEFAULT 0,
  cost_fen    DECIMAL(12,4) NOT NULL DEFAULT 0,
  ok          TINYINT      NOT NULL DEFAULT 1,
  input_json  TEXT         NULL COMMENT '输入摘要（已脱敏）',
  output_json TEXT         NULL COMMENT '输出摘要（已脱敏）',
  KEY idx_agent_span_trace (trace_id)
) COMMENT='咕嘟调用链片段';

CREATE TABLE IF NOT EXISTS agent_eval_case (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  case_id       VARCHAR(80)  NOT NULL,
  split         VARCHAR(8)   NOT NULL DEFAULT 'dev',
  category      VARCHAR(32)  NOT NULL,
  json          MEDIUMTEXT   NOT NULL COMMENT '与 agent-eval/cases/*.jsonl 同格式的一行',
  source        VARCHAR(16)  NOT NULL DEFAULT 'imported' COMMENT 'imported / feedback',
  from_trace_id VARCHAR(40)  NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'DRAFT（待标注期望）/ ACTIVE',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_agent_eval_case (case_id)
) COMMENT='咕嘟评测用例（库内：后台导入 / 差评回流）';

CREATE TABLE IF NOT EXISTS agent_eval_run (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  prompt_version     VARCHAR(32)  NULL,
  provider           VARCHAR(32)  NULL COMMENT 'mock / gateway',
  status             VARCHAR(16)  NOT NULL COMMENT 'RUNNING / DONE / FAILED',
  tool_accuracy      DECIMAL(6,4) NULL,
  arg_accuracy       DECIMAL(6,4) NULL,
  point_coverage     DECIMAL(6,4) NULL,
  avg_cost_fen       DECIMAL(12,4) NULL,
  p95_ms             INT          NULL,
  passed             TINYINT      NULL,
  hard_gate_failures INT          NULL,
  baseline           TINYINT      NOT NULL DEFAULT 0,
  report_json        LONGTEXT     NULL,
  error              VARCHAR(512) NULL,
  created_at         DATETIME     DEFAULT CURRENT_TIMESTAMP,
  finished_at        DATETIME     NULL
) COMMENT='咕嘟评测运行';
