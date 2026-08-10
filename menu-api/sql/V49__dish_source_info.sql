-- V49: dish 加来源信息（source_name 来源名 + source_url 第三方地址）
-- 背景：① 菜谱来源需要可读名字（自己创建/下厨房/美食杰/豆果/抖音…），现有 source 只是 ORIGINAL/IMPORT 标记；
--       ② 导入的第三方地址此前塞在 note 里（「【URL 导入】 来源：…」），需独立字段。
-- 幂等：ALTER 用 information_schema 判存在（照 V36/V44 范式）

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dish' AND COLUMN_NAME='source_name');
SET @s1 := IF(@c1=0,
  'ALTER TABLE dish ADD COLUMN source_name VARCHAR(32) NULL COMMENT ''来源名（自己创建/下厨房/美食杰/豆果/抖音…）'' AFTER source',
  'SELECT 1');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dish' AND COLUMN_NAME='source_url');
SET @s2 := IF(@c2=0,
  'ALTER TABLE dish ADD COLUMN source_url VARCHAR(512) NULL COMMENT ''第三方来源地址（导入时记录）'' AFTER source_name',
  'SELECT 1');
PREPARE p2 FROM @s2; EXECUTE p2; DEALLOCATE PREPARE p2;

-- 存量导入菜谱回填：note 里的「【URL 导入】 来源：xxx」迁移到 source_url（幂等，已填的不动）
UPDATE dish d
SET d.source_url = TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(d.note, '来源：', -1), '】', 1))
WHERE d.source_url IS NULL
  AND d.note LIKE '%【URL 导入】%来源：%';
