-- ============================================================
-- V35 单位换算体系：换算表 + dish_ingredient/pantry 加 grams + 回填
-- 背景：docs/redesign-audit.md §4/§5/§12 三方单位口径不一致
-- 幂等：建表 CREATE IF NOT EXISTS；ALTER 用 information_schema 判列存在
-- ============================================================

-- 1) 补充 unit 字典（V02 仅有 g/ml/个/把，补自然单位用于换算表）
INSERT IGNORE INTO sys_dict(dict_group, name) VALUES
  ('unit','根'),('unit','块'),('unit','头'),('unit','颗'),('unit','条'),
  ('unit','勺'),('unit','斤'),('unit','瓶'),('unit','袋'),('unit','盒'),('unit','杯');

-- 2) 换算表
CREATE TABLE IF NOT EXISTS ingredient_unit_gram (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  ingredient_id   BIGINT NOT NULL,
  unit_id         BIGINT NOT NULL,
  grams_per_unit  DECIMAL(10,2) NOT NULL,
  is_default      TINYINT(1) DEFAULT 0,
  UNIQUE KEY uk_ing_unit (ingredient_id, unit_id),
  KEY idx_ing (ingredient_id)
);

-- 3) 预置高频食材换算（INSERT...SELECT by name，照 V15/V24 范式）
--    覆盖蛋/肉/蔬/调味/主食；is_default=1 标默认单位。其余食材按同范式扩展。
SET @g   := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='g' LIMIT 1);
SET @ge  := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='个' LIMIT 1);
SET @gen := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='根' LIMIT 1);
SET @ba  := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='把' LIMIT 1);
SET @kuai:= (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='块' LIMIT 1);
SET @jin := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='斤' LIMIT 1);
SET @shao:= (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='勺' LIMIT 1);
SET @ping:= (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='瓶' LIMIT 1);
SET @dai := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='袋' LIMIT 1);

INSERT IGNORE INTO ingredient_unit_gram(ingredient_id, unit_id, grams_per_unit, is_default)
  SELECT i.id, @ge, 50, 1 FROM ingredient i WHERE i.name='鸡蛋'
  UNION SELECT i.id, @jin, 500, 1 FROM ingredient i WHERE i.name='五花肉'
  UNION SELECT i.id, @jin, 500, 1 FROM ingredient i WHERE i.name='里脊'
  UNION SELECT i.id, @jin, 500, 1 FROM ingredient i WHERE i.name='牛肉'
  UNION SELECT i.id, @ge, 150, 1 FROM ingredient i WHERE i.name='番茄'
  UNION SELECT i.id, @ge, 150, 1 FROM ingredient i WHERE i.name='土豆'
  UNION SELECT i.id, @gen, 200, 1 FROM ingredient i WHERE i.name='黄瓜'
  UNION SELECT i.id, @gen, 200, 1 FROM ingredient i WHERE i.name='胡萝卜'
  UNION SELECT i.id, @ge, 150, 1 FROM ingredient i WHERE i.name='茄子'
  UNION SELECT i.id, @kuai, 100, 1 FROM ingredient i WHERE i.name='豆腐'
  UNION SELECT i.id, @ba, 100, 1 FROM ingredient i WHERE i.name='大葱'
  UNION SELECT i.id, @kuai, 30, 1 FROM ingredient i WHERE i.name='生姜'
  UNION SELECT i.id, @shao, 5, 1 FROM ingredient i WHERE i.name='盐'
  UNION SELECT i.id, @shao, 5, 1 FROM ingredient i WHERE i.name='冰糖'
  UNION SELECT i.id, @shao, 15, 1 FROM ingredient i WHERE i.name='酱油(生抽)'
  UNION SELECT i.id, @shao, 15, 1 FROM ingredient i WHERE i.name='酱油(老抽)'
  UNION SELECT i.id, @shao, 15, 1 FROM ingredient i WHERE i.name='料酒'
  UNION SELECT i.id, @shao, 15, 1 FROM ingredient i WHERE i.name='食用油'
  UNION SELECT i.id, @dai, 1000, 1 FROM ingredient i WHERE i.name='大米'
  UNION SELECT i.id, @dai, 1000, 1 FROM ingredient i WHERE i.name='面粉';
-- 注：扩展至约 200 种，按 ingredient name 同范式补全（蛋/肉/蔬/调味/主食/水产）

-- 4) dish_ingredient 加 unit_id + grams（幂等 ALTER，照 V30 范式）
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dish_ingredient' AND COLUMN_NAME='unit_id');
SET @s1 := IF(@c1=0, 'ALTER TABLE dish_ingredient ADD COLUMN unit_id BIGINT NULL', 'SELECT "exists"');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dish_ingredient' AND COLUMN_NAME='grams');
SET @s2 := IF(@c2=0, 'ALTER TABLE dish_ingredient ADD COLUMN grams DECIMAL(12,2) NULL', 'SELECT "exists"');
PREPARE p2 FROM @s2; EXECUTE p2; DEALLOCATE PREPARE p2;

-- 旧数据 amount 是克：unit_id=g, grams=amount（不依赖换算表，100% 回填）
UPDATE dish_ingredient SET unit_id = @g WHERE unit_id IS NULL;
UPDATE dish_ingredient SET grams = amount WHERE grams IS NULL;

-- 5) pantry 加 grams（幂等 ALTER）+ 回填（JOIN 换算表；未覆盖留 NULL=兜底标灰）
SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pantry' AND COLUMN_NAME='grams');
SET @s3 := IF(@c3=0, 'ALTER TABLE pantry ADD COLUMN grams DECIMAL(12,2) NULL', 'SELECT "exists"');
PREPARE p3 FROM @s3; EXECUTE p3; DEALLOCATE PREPARE p3;

UPDATE pantry p
  JOIN ingredient_unit_gram iug ON p.ingredient_id = iug.ingredient_id AND p.unit_id = iug.unit_id
  SET p.grams = p.amount * iug.grams_per_unit
  WHERE p.grams IS NULL;
