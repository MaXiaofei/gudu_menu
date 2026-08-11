-- ============================================================
-- V51 修复：补建 ingredient_unit_gram 表 + 回填 dish_ingredient.grams
--
-- 背景：V35(单位换算体系)在存量库 staging 上漏跑——
--   deploy.sh 只执行 V42+ 的增量迁移(V01~V41 由 MySQL 首启 initdb 执行，
--   但存量库早已首启过，后加的 V35 永远不会被触发)。
--   导致 ingredient_unit_gram 表缺失，MenuService.summary → UnitConvertService 报 500；
--   同时旧 dish_ingredient 行 grams 为 NULL，NeedAggregator 聚合为空。
--
-- 本补丁纳入 V42+ 增量范围（deploy.sh 自动执行），幂等：
--   - 表已存在则跳过（CREATE IF NOT EXISTS）
--   - grams 回填只更新 NULL 行（WHERE grams IS NULL）
--   - 换算行 INSERT IGNORE 避免重复键冲突
-- 适用：staging（已手动补过，本补丁幂等无副作用）/ prod / 全新库。
-- ============================================================

-- 1) 换算表（幂等：已存在则跳过）
CREATE TABLE IF NOT EXISTS ingredient_unit_gram (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  ingredient_id   BIGINT NOT NULL,
  unit_id         BIGINT NOT NULL,
  grams_per_unit  DECIMAL(10,2) NOT NULL,
  is_default      TINYINT(1) DEFAULT 0,
  UNIQUE KEY uk_ing_unit (ingredient_id, unit_id),
  KEY idx_ing (ingredient_id)
);

-- 2) dish_ingredient.grams 回填（旧数据 amount 即克数，unit=g/id=20）
--    只回填 grams IS NULL 的行，已回填的不动（幂等）。
UPDATE dish_ingredient SET grams = amount WHERE grams IS NULL;

-- 3) 预置高频食材换算行（V35 原值；INSERT IGNORE 幂等）
--    按 name 匹配 ingredient，确保食材 id 漂移后仍能命中。
SET @ge := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='个' LIMIT 1);
SET @gen := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='根' LIMIT 1);
SET @ba := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='把' LIMIT 1);
SET @kuai := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='块' LIMIT 1);
SET @jin := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='斤' LIMIT 1);
SET @shao := (SELECT id FROM sys_dict WHERE dict_group='unit' AND name='勺' LIMIT 1);
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
