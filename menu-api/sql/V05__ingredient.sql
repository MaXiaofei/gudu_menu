-- 食材库 + 营养 EAV
CREATE TABLE IF NOT EXISTS ingredient (
  id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
  name                 VARCHAR(64) NOT NULL,
  unit_id              BIGINT,                      -- 关联 sys_dict(unit)
  price                DECIMAL(8,2) DEFAULT 0,
  purchase_category_id BIGINT,                      -- 关联 sys_dict(purchase_category)
  purchase_count       INT DEFAULT 0,
  usage_count          INT DEFAULT 0,
  deleted              TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ingredient_nutrition (   -- EAV：食材-指标-值(per 100g)
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  ingredient_id BIGINT NOT NULL,
  metric_id     BIGINT NOT NULL,
  value         DECIMAL(10,2) NOT NULL,
  UNIQUE KEY uk_ing_metric (ingredient_id, metric_id)
);
