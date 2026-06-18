-- 菜品历史版本（编辑前快照）
CREATE TABLE IF NOT EXISTS dish_history (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  dish_id     BIGINT NOT NULL,
  snapshot    JSON NOT NULL,                  -- 编辑前完整快照（菜品+步骤+关联）
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
