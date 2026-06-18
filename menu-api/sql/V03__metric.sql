-- 营养指标维度表 + 6 项种子
CREATE TABLE IF NOT EXISTS nutrition_metric (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  name         VARCHAR(32) NOT NULL UNIQUE,
  unit         VARCHAR(16) NOT NULL,         -- g / mg / kcal / index
  metric_group VARCHAR(16) NOT NULL,         -- macro 宏量 / micro 微量 / gi
  sort         INT DEFAULT 0
);

INSERT INTO nutrition_metric(name, unit, metric_group) VALUES
 ('calorie','kcal','macro'),
 ('protein','g','macro'),
 ('fat','g','macro'),
 ('carb','g','macro'),
 ('sugar','g','macro'),
 ('gi','index','gi');
