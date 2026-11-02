SET NAMES utf8mb4;
-- V96: shopping_list 加 seq（本家庭内第几张采购单）。
-- 背景：小程序列表角标用 id%100+1、详情用全局 id，既不一致又跨家庭共享；改为按家庭各自从 1 递增。
-- 幂等：列/索引均先判存在；回填只处理 seq IS NULL 的行（含已删除行，保证序号不复用）。

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_list' AND COLUMN_NAME = 'seq');
SET @s := IF(@c = 0, 'ALTER TABLE shopping_list ADD COLUMN seq INT NULL COMMENT ''本家庭内第几张采购单''', 'SELECT 1');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

-- 回填：同一家庭内按 id 升序编号（1,2,3...）
UPDATE shopping_list a
JOIN (
  SELECT x.id, (SELECT COUNT(*) FROM shopping_list y WHERE y.family_id <=> x.family_id AND y.id <= x.id) AS rn
  FROM shopping_list x WHERE x.seq IS NULL
) b ON b.id = a.id
SET a.seq = b.rn
WHERE a.seq IS NULL;

SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shopping_list' AND INDEX_NAME = 'uk_shopping_list_family_seq');
SET @s := IF(@c = 0, 'ALTER TABLE shopping_list ADD UNIQUE INDEX uk_shopping_list_family_seq (family_id, seq)', 'SELECT 1');
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;
