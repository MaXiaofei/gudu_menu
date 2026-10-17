-- ============================================================
-- V73: 模型网关（showcase-ai-community §6）
-- 供应商 / 模型 / 能力路由入库，后台可配置与热切换；key 入库为 AES-GCM 密文（主密钥 GUDU_SECRET_KEY），
-- 种子供应商的 key 取环境变量（key_source=ENV，库里不存任何 key）。
-- ai_call_log 增加 capability / model / cost_fen / trace_id：网关每次尝试一行（capability 非空）。
-- 幂等：CREATE TABLE IF NOT EXISTS + 种子 INSERT IGNORE（固定 id）+ 列存在则跳过 ALTER。
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_provider (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  name              VARCHAR(64)  NOT NULL COMMENT '供应商名（DeepSeek / GLM / Mock …）',
  type              VARCHAR(32)  NOT NULL DEFAULT 'OPENAI_COMPATIBLE' COMMENT 'OPENAI_COMPATIBLE / MOCK',
  base_url          VARCHAR(255) NULL,
  api_key_enc       TEXT         NULL COMMENT 'AES-GCM 密文（v1:base64(iv|ct)），key_source=DB 时有值',
  key_source        VARCHAR(8)   NOT NULL DEFAULT 'NONE' COMMENT 'DB / ENV / NONE',
  key_env           VARCHAR(64)  NULL COMMENT 'key_source=ENV 时的环境变量名',
  enabled           TINYINT      NOT NULL DEFAULT 1,
  health_ok         TINYINT      NULL,
  health_latency_ms INT          NULL,
  health_checked_at DATETIME     NULL,
  create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted           TINYINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ai_provider_name (name)
) COMMENT='模型供应商';

CREATE TABLE IF NOT EXISTS ai_provider_model (
  id                     BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider_id            BIGINT       NOT NULL,
  model                  VARCHAR(64)  NOT NULL,
  capabilities           VARCHAR(64)  NOT NULL COMMENT '逗号分隔：CHAT,TOOLS,VISION',
  price_cache_hit_fen    DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '输入缓存命中 分/百万token（高峰价）',
  price_input_fen        DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '输入未命中 分/百万token（高峰价）',
  price_output_fen       DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '输出 分/百万token（高峰价）',
  peak_hours             VARCHAR(255) NULL COMMENT 'JSON {timezone,weekdaysOnly,ranges[],offPeakRatio}；空=全天同价',
  supports_thinking      TINYINT      NOT NULL DEFAULT 0,
  sort_no                INT          NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ai_provider_model (provider_id, model)
) COMMENT='供应商下的模型与单价';

CREATE TABLE IF NOT EXISTS ai_route (
  capability          VARCHAR(16)  PRIMARY KEY COMMENT 'CHAT / TOOLS / VISION',
  primary_provider_id BIGINT       NOT NULL,
  primary_model       VARCHAR(64)  NOT NULL,
  fallbacks           VARCHAR(1024) NOT NULL DEFAULT '[]' COMMENT 'JSON [{providerId,model}]',
  timeout_ms          INT          NOT NULL DEFAULT 30000,
  max_retries         INT          NOT NULL DEFAULT 1,
  thinking            VARCHAR(16)  NOT NULL DEFAULT 'DISABLED' COMMENT 'DISABLED / LOW / HIGH / MAX',
  updated_at          DATETIME     NULL,
  updated_by          VARCHAR(64)  NULL
) COMMENT='能力路由';

CREATE TABLE IF NOT EXISTS ai_gateway_audit (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor       VARCHAR(64)  NULL,
  action      VARCHAR(32)  NOT NULL,
  target      VARCHAR(64)  NULL,
  detail      TEXT         NULL COMMENT '变更内容 JSON（不含 key 明文）',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ai_gateway_audit_time (create_time)
) COMMENT='模型网关配置变更审计';

-- 种子：固定 id，重复执行不覆盖后台改过的配置
INSERT IGNORE INTO ai_provider (id, name, type, base_url, key_source, key_env, enabled) VALUES
  (1, 'DeepSeek', 'OPENAI_COMPATIBLE', 'https://api.deepseek.com/v1', 'ENV', 'DEEPSEEK_API_KEY', 1),
  (2, 'GLM',      'OPENAI_COMPATIBLE', 'https://open.bigmodel.cn/api/paas/v4', 'ENV', 'GLM_API_KEY', 0),
  (3, 'Mock',     'MOCK', NULL, 'NONE', NULL, 1);

INSERT IGNORE INTO ai_provider_model
  (provider_id, model, capabilities, price_cache_hit_fen, price_input_fen, price_output_fen, peak_hours, supports_thinking, sort_no) VALUES
  (1, 'deepseek-flash',  'CHAT,TOOLS,VISION', 4,   200, 800,
   '{"timezone":"Asia/Shanghai","weekdaysOnly":true,"ranges":["09:00-12:00","14:00-18:00"],"offPeakRatio":0.5}', 1, 1),
  (1, 'deepseek-v4-pro', 'CHAT,TOOLS',        30,  900, 2700,
   '{"timezone":"Asia/Shanghai","weekdaysOnly":true,"ranges":["09:00-12:00","14:00-18:00"],"offPeakRatio":0.5}', 1, 2),
  (2, 'glm-4-plus',      'CHAT,TOOLS',        500, 500, 500, NULL, 0, 1),
  (2, 'glm-4v-plus',     'VISION',            400, 400, 400, NULL, 0, 2),
  (3, 'mock',            'CHAT,TOOLS,VISION', 0,   0,   0,   NULL, 0, 1);

INSERT IGNORE INTO ai_route (capability, primary_provider_id, primary_model, fallbacks, timeout_ms, max_retries, thinking, updated_at, updated_by) VALUES
  ('CHAT',   1, 'deepseek-flash', '[{"providerId":2,"model":"glm-4-plus"},{"providerId":3,"model":"mock"}]',  30000, 1, 'DISABLED', NOW(), 'system'),
  ('TOOLS',  1, 'deepseek-flash', '[{"providerId":2,"model":"glm-4-plus"},{"providerId":3,"model":"mock"}]',  30000, 1, 'DISABLED', NOW(), 'system'),
  ('VISION', 1, 'deepseek-flash', '[{"providerId":2,"model":"glm-4v-plus"},{"providerId":3,"model":"mock"}]', 60000, 1, 'DISABLED', NOW(), 'system');

-- ai_call_log：网关尝试行
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_call_log' AND COLUMN_NAME = 'capability');
SET @s := IF(@c = 0,
  'ALTER TABLE ai_call_log ADD COLUMN capability VARCHAR(16) NULL COMMENT ''网关能力（非空=网关尝试行）'', ADD COLUMN model VARCHAR(64) NULL, ADD COLUMN cost_fen DECIMAL(12,4) NULL COMMENT ''成本 分（按调用时刻单价）'', ADD COLUMN trace_id VARCHAR(64) NULL',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE ai_call_log MODIFY COLUMN provider VARCHAR(64) DEFAULT 'mock' COMMENT '供应商名';

SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_call_log' AND INDEX_NAME = 'idx_aicall_capability');
SET @s := IF(@c = 0, 'CREATE INDEX idx_aicall_capability ON ai_call_log(capability, create_time)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
