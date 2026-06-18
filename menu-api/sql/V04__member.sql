-- 家庭成员表（health_profile 用 JSON 字段，灵活存三高指标/忌口/特殊人群/营养约束）
CREATE TABLE IF NOT EXISTS member (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  name          VARCHAR(64) NOT NULL,
  role_tags     VARCHAR(128),              -- 逗号分隔，关联 sys_dict(role)
  health_profile JSON,                     -- {height,weight,allergies:[],audiences:[],constraints:{sugarMax,...}}
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted       TINYINT  DEFAULT 0
);
