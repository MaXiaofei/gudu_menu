-- 菜单 + 菜单菜品关联
CREATE TABLE IF NOT EXISTS menu (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  name             VARCHAR(128) NOT NULL,
  type_id          BIGINT,                       -- 关联 sys_dict(menu_type)
  target_member_id BIGINT,
  serving_count    INT DEFAULT 1,                -- 份数 / 人数
  create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted          TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS menu_dish (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  menu_id        BIGINT NOT NULL,
  dish_id        BIGINT NOT NULL,
  serving_factor DECIMAL(4,2) DEFAULT 1          -- 该菜在该菜单的份数
);
