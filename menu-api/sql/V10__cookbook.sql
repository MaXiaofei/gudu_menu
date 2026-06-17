-- 收藏（按成员-菜品唯一）
CREATE TABLE IF NOT EXISTS favorite (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  member_id BIGINT,
  dish_id   BIGINT,
  UNIQUE KEY uk (member_id, dish_id)
);
