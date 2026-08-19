-- ============================================================
-- V60__dish_cuisine_tag_backfill.sql
-- 菜系/标签关联重建：标签体系重构后 dish_dict 的 cuisine/tag 关联归零，
-- 按菜名关键词规则批量补挂（宁缺毋滥，规则可读可维护）。
-- 两端（staging/prod）均执行；幂等：NOT EXISTS 防重。
-- ============================================================

-- ================= 菜系（cuisine）=================

INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'cuisine', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='cuisine' AND s.name='川菜'
WHERE d.deleted=0 AND (
  d.name LIKE '%麻婆豆腐%' OR d.name LIKE '%水煮%' OR d.name LIKE '%鱼香%'
  OR d.name LIKE '%宫保%' OR d.name LIKE '%辣子鸡%' OR d.name LIKE '%回锅肉%'
  OR d.name LIKE '%酸菜鱼%' OR d.name LIKE '%干煸%' OR d.name LIKE '%麻辣%'
  OR d.name LIKE '%蚂蚁上树%' OR d.name LIKE '%担担面%' OR d.name LIKE '%口水鸡%'
  OR d.name LIKE '%毛血旺%' OR d.name LIKE '%花椒%' OR d.name LIKE '%鱼香%')
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='cuisine' AND x.dict_id=s.id);

INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'cuisine', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='cuisine' AND s.name='粤菜'
WHERE d.deleted=0 AND (
  d.name LIKE '%白灼%' OR d.name LIKE '%叉烧%' OR d.name LIKE '%豉油%'
  OR d.name LIKE '%煲仔%' OR d.name LIKE '%老火汤%' OR d.name LIKE '%虾饺%'
  OR d.name LIKE '%烧鹅%' OR d.name LIKE '%豉汁%')
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='cuisine' AND x.dict_id=s.id);

INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'cuisine', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='cuisine' AND s.name='湘菜'
WHERE d.deleted=0 AND (
  d.name LIKE '%剁椒%' OR d.name LIKE '%腊肉%' OR d.name LIKE '%小炒肉%'
  OR d.name LIKE '%口味虾%' OR d.name LIKE '%辣椒炒肉%')
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='cuisine' AND x.dict_id=s.id);

INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'cuisine', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='cuisine' AND s.name='东北菜'
WHERE d.deleted=0 AND (
  d.name LIKE '%锅包肉%' OR d.name LIKE '%地三鲜%' OR d.name LIKE '%小鸡炖蘑菇%'
  OR d.name LIKE '%炖粉条%' OR d.name LIKE '%乱炖%' OR d.name LIKE '%拔丝%'
  OR d.name LIKE '%酸菜炖%')
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='cuisine' AND x.dict_id=s.id);

INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'cuisine', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='cuisine' AND s.name='西北菜'
WHERE d.deleted=0 AND (
  d.name LIKE '%大盘鸡%' OR d.name LIKE '%羊肉%' OR d.name LIKE '%拉条%'
  OR d.name LIKE '%油泼%' OR d.name LIKE '%肉夹馍%' OR d.name LIKE '%凉皮%'
  OR d.name LIKE '%孜然羊%' OR d.name LIKE '%手抓%')
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='cuisine' AND x.dict_id=s.id);

INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'cuisine', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='cuisine' AND s.name='苏菜'
WHERE d.deleted=0 AND (
  d.name LIKE '%狮子头%' OR d.name LIKE '%盐水鸭%' OR d.name LIKE '%松鼠%')
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='cuisine' AND x.dict_id=s.id);

INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'cuisine', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='cuisine' AND s.name='浙菜'
WHERE d.deleted=0 AND (
  d.name LIKE '%东坡%' OR d.name LIKE '%西湖%' OR d.name LIKE '%龙井虾仁%'
  OR d.name LIKE '%叫花鸡%')
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='cuisine' AND x.dict_id=s.id);

INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'cuisine', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='cuisine' AND s.name='闽菜'
WHERE d.deleted=0 AND (
  d.name LIKE '%佛跳墙%' OR d.name LIKE '%荔枝肉%' OR d.name LIKE '%沙茶%')
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='cuisine' AND x.dict_id=s.id);

INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'cuisine', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='cuisine' AND s.name='鲁菜'
WHERE d.deleted=0 AND (
  d.name LIKE '%九转%' OR d.name LIKE '%糖醋鲤鱼%' OR d.name LIKE '%葱烧%')
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='cuisine' AND x.dict_id=s.id);

INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'cuisine', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='cuisine' AND s.name='徽菜'
WHERE d.deleted=0 AND (
  d.name LIKE '%臭鳜鱼%' OR d.name LIKE '%毛豆腐%')
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='cuisine' AND x.dict_id=s.id);

-- ================= 标签（tag，按字典现有词条）=================

-- 汤羹
INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'tag', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='tag' AND s.name='汤'
WHERE d.deleted=0 AND d.name LIKE '%汤%'
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='tag' AND x.dict_id=s.id);

-- 粥 → 养胃
INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'tag', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='tag' AND s.name='养胃'
WHERE d.deleted=0 AND (d.name LIKE '%粥%' OR d.name LIKE '%小米%')
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='tag' AND x.dict_id=s.id);

-- 蒸
INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'tag', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='tag' AND s.name='蒸'
WHERE d.deleted=0 AND d.name LIKE '%蒸%'
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='tag' AND x.dict_id=s.id);

-- 炖
INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'tag', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='tag' AND s.name='炖'
WHERE d.deleted=0 AND d.name LIKE '%炖%'
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='tag' AND x.dict_id=s.id);

-- 凉拌
INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'tag', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='tag' AND s.name='凉拌'
WHERE d.deleted=0 AND d.name LIKE '%凉拌%'
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='tag' AND x.dict_id=s.id);

-- 烘焙
INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'tag', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='tag' AND s.name='烘焙'
WHERE d.deleted=0 AND (
  d.name LIKE '%蛋糕%' OR d.name LIKE '%饼干%' OR d.name LIKE '%面包%'
  OR d.name LIKE '%泡芙%' OR d.name LIKE '%司康%' OR d.name LIKE '%曲奇%'
  OR d.name LIKE '%布丁%' OR d.name LIKE '%玛芬%' OR d.name LIKE '%酥%'
  OR d.name LIKE '%挞%' OR d.name LIKE '%蛋挞%' OR d.name LIKE '%派%')
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='tag' AND x.dict_id=s.id);

-- 重口味（辣/麻/卤/孜然）
INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'tag', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='tag' AND s.name='重口味'
WHERE d.deleted=0 AND (
  d.name LIKE '%辣%' OR d.name LIKE '%麻%' OR d.name LIKE '%卤%'
  OR d.name LIKE '%孜然%' OR d.name LIKE '%剁椒%')
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='tag' AND x.dict_id=s.id);

-- 烧烤（烤）
INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'tag', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='tag' AND s.name='烧烤'
WHERE d.deleted=0 AND d.name LIKE '%烤%'
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='tag' AND x.dict_id=s.id);

-- 早餐（粥/豆浆/馒头/包子/鸡蛋饼）
INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'tag', s.id FROM dish d
JOIN sys_dict s ON s.dict_group='tag' AND s.name='早餐'
WHERE d.deleted=0 AND (
  d.name LIKE '%粥%' OR d.name LIKE '%豆浆%' OR d.name LIKE '%馒头%'
  OR d.name LIKE '%包子%' OR d.name LIKE '%鸡蛋饼%' OR d.name LIKE '%三明治%')
AND NOT EXISTS (SELECT 1 FROM dish_dict x WHERE x.dish_id=d.id AND x.rel_type='tag' AND x.dict_id=s.id);

-- 验证：
--   SELECT s.name, COUNT(*) FROM dish_dict d JOIN sys_dict s ON s.id=d.dict_id
--     WHERE d.rel_type='cuisine' GROUP BY s.name;
--   SELECT s.name, COUNT(*) FROM dish_dict d JOIN sys_dict s ON s.id=d.dict_id
--     WHERE d.rel_type='tag' GROUP BY s.name ORDER BY COUNT(*) DESC;
