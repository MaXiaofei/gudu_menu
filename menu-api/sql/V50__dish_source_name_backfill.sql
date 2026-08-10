-- V50: 存量导入菜谱回填 source_name（按 source_url 域名映射，幂等：已填的不动）
-- 背景：V49 只回填了 source_url，source_name 需按站点域名补齐（下厨房/美食杰/豆果/外部导入）。

UPDATE dish SET source_name = '下厨房'
WHERE source_name IS NULL AND source_url LIKE '%xiachufang.com%';

UPDATE dish SET source_name = '美食杰'
WHERE source_name IS NULL AND source_url LIKE '%meishij.net%';

UPDATE dish SET source_name = '豆果美食'
WHERE source_name IS NULL AND source_url LIKE '%douguo.com%';

UPDATE dish SET source_name = '外部导入'
WHERE source_name IS NULL
  AND source_url IS NOT NULL
  AND source_url NOT LIKE '%xiachufang.com%'
  AND source_url NOT LIKE '%meishij.net%'
  AND source_url NOT LIKE '%douguo.com%';
