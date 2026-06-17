-- 菜品 + 步骤
CREATE TABLE IF NOT EXISTS dish (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  name       VARCHAR(128) NOT NULL,
  note       VARCHAR(512),
  cover_url  VARCHAR(512),
  prep_time  INT,                       -- 备料时间 分钟
  cook_time  INT,                       -- 烹饪时间 分钟
  price      DECIMAL(8,2) DEFAULT 0,
  difficulty TINYINT,                   -- 1-5
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted    TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS dish_step (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  dish_id    BIGINT NOT NULL,
  seq        INT NOT NULL,
  text       VARCHAR(1024),
  images     VARCHAR(1024),             -- 多图逗号分隔 URL
  sort_order INT
);
