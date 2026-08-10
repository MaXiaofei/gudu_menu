-- V45: 做菜确认用材记录（menu_cook_usage）
-- 背景：食记单条详情展示「用完 4 样 / 用了一些 2 样」（原型 dailylog.html）
-- 幂等：CREATE TABLE IF NOT EXISTS（deploy.sh 会对 V42+ 全部执行，必须幂等）
CREATE TABLE IF NOT EXISTS menu_cook_usage (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  menu_id       BIGINT      NOT NULL COMMENT '食集 menu.id',
  ingredient_id BIGINT      NOT NULL COMMENT '食材 ingredient.id',
  action        VARCHAR(16) NOT NULL COMMENT 'used_up 用完 / partial 用了一些',
  create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_usage_menu (menu_id)
) COMMENT '做菜确认用材记录（食记单条详情）';
