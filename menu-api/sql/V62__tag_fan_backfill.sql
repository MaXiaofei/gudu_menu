-- ============================================================
-- V62: 新建「饭」标签 + 主食类菜关联重建（2026-08-20）
--
-- 背景：V61 清理了 55 个自动补录泛词（饭/炒饭/电饭煲/布丁/年夜饭/粥/面条等）
--   并解除了关联。用户要求：新建一个「饭」tag，把那批词涉及的主食类菜
--   统一关联到「饭」。
-- 实现：V61 已删关联无法恢复 → 按菜名关键词重建（宁缺毋滥，V60 范式）：
--   炒饭/电饭煲/煲仔饭/拌饭/饭团/盖浇饭/焖饭/布丁/年夜饭/粥/面条/馒头/
--   包子/年糕/焖面/拌面/汤面/炒面/烙饼/烧饼 等主食关键词。
-- 幂等：词条 INSERT...WHERE NOT EXISTS；关联 NOT EXISTS 防重。
-- ============================================================

-- 1) 新建「饭」标签（幂等）
INSERT INTO sys_dict(dict_group, name, sort)
SELECT 'tag', '饭', 46 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_group='tag' AND name='饭');

-- 2) 主食类菜名关键词 → 关联「饭」
INSERT INTO dish_dict(dish_id, rel_type, dict_id)
SELECT d.id, 'tag', s.id
FROM dish d
JOIN sys_dict s ON s.dict_group='tag' AND s.name='饭'
WHERE d.deleted=0
  AND (
    d.name LIKE '%炒饭%' OR d.name LIKE '%电饭煲%' OR d.name LIKE '%煲仔饭%'
    OR d.name LIKE '%拌饭%' OR d.name LIKE '%饭团%' OR d.name LIKE '%盖浇饭%'
    OR d.name LIKE '%焖饭%' OR d.name LIKE '%布丁%' OR d.name LIKE '%年夜饭%'
    OR d.name LIKE '%粥%' OR d.name LIKE '%面条%' OR d.name LIKE '%面%饭%'
    OR d.name LIKE '%馒头%' OR d.name LIKE '%包子%' OR d.name LIKE '%年糕%'
    OR d.name LIKE '%焖面%' OR d.name LIKE '%拌面%' OR d.name LIKE '%汤面%'
    OR d.name LIKE '%炒面%' OR d.name LIKE '%烙饼%' OR d.name LIKE '%烧饼%'
    OR d.name LIKE '%煎饼%'
  )
  AND NOT EXISTS (SELECT 1 FROM dish_dict x
    WHERE x.dish_id=d.id AND x.rel_type='tag' AND x.dict_id=s.id);

-- 验证：
--   SELECT COUNT(*) FROM dish_dict d JOIN sys_dict s ON s.id=d.dict_id
--     WHERE d.rel_type='tag' AND s.name='饭';  -- 主食类菜数
