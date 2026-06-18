-- V16: 周计划模块（meal_plan / meal_plan_item / menu_template）
-- 餐次字典：复用 sys_dict(group=meal)，不写死枚举
INSERT INTO sys_dict(dict_group, name, sort) VALUES
  ('meal', '早餐', 1),
  ('meal', '午餐', 2),
  ('meal', '晚餐', 3),
  ('meal', '加餐', 4);

-- 周计划主表
CREATE TABLE IF NOT EXISTS meal_plan (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  week_start  DATE NOT NULL,
  name        VARCHAR(64),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted     TINYINT DEFAULT 0
);

-- 周计划排菜项
CREATE TABLE IF NOT EXISTS meal_plan_item (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id        BIGINT NOT NULL,
  `date`         DATE NOT NULL,
  meal           VARCHAR(16) NOT NULL,          -- 早餐/午餐/晚餐/加餐（关联 sys_dict(group=meal)）
  dish_id        BIGINT NOT NULL,
  serving_factor DECIMAL(4,2) DEFAULT 1,
  sort           INT DEFAULT 0,
  UNIQUE KEY uk_plan_date_meal_dish (plan_id, `date`, meal, dish_id)
);

-- 菜排列模板（一周菜排列快照，snapshot JSON）
CREATE TABLE IF NOT EXISTS menu_template (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  name        VARCHAR(64) NOT NULL,
  snapshot    JSON NOT NULL,                    -- 一周菜排列快照（List<MealPlanItem> 视图）
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mealplan_week ON meal_plan(week_start);
CREATE INDEX idx_mealplanitem_plan ON meal_plan_item(plan_id);
