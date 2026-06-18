-- 菜品多对多关联：菜系/标签/分类 + 食材用量
CREATE TABLE IF NOT EXISTS dish_dict (
  id       BIGINT PRIMARY KEY AUTO_INCREMENT,
  dish_id  BIGINT NOT NULL,
  dict_id  BIGINT NOT NULL,
  rel_type VARCHAR(16) NOT NULL,        -- cuisine / tag / category
  UNIQUE KEY uk_rel (dish_id, dict_id, rel_type)
);

CREATE TABLE IF NOT EXISTS dish_ingredient (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  dish_id       BIGINT NOT NULL,
  ingredient_id BIGINT NOT NULL,
  amount        DECIMAL(10,2) NOT NULL,  -- 用量克数
  UNIQUE KEY uk (dish_id, ingredient_id)
);
