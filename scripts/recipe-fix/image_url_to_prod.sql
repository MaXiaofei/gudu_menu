-- 菜谱图片地址从预发域名改为生产域名（只在生产库执行；前提：imxf.cloud 已提供 /images/，见 nginx app-https 模板）
-- 改动登记到 recipe_fix_log（批次 image-url-prod-20261027），撤回见文件末尾。可重复执行（第二次不再命中）。
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO recipe_fix_log(batch, kind, ref_id, old_value)
SELECT 'image-url-prod-20261027', 'dish_cover_url', id, cover_url FROM dish
WHERE cover_url LIKE 'https://staging.imxf.cloud/images/%';
UPDATE dish SET cover_url = REPLACE(cover_url, 'https://staging.imxf.cloud/images/', 'https://imxf.cloud/images/')
WHERE cover_url LIKE 'https://staging.imxf.cloud/images/%';

INSERT INTO recipe_fix_log(batch, kind, ref_id, old_value)
SELECT 'image-url-prod-20261027', 'dish_step_images', id, images FROM dish_step
WHERE images LIKE '%https://staging.imxf.cloud/images/%';
UPDATE dish_step SET images = REPLACE(images, 'https://staging.imxf.cloud/images/', 'https://imxf.cloud/images/')
WHERE images LIKE '%https://staging.imxf.cloud/images/%';

COMMIT;

-- 撤回：
-- UPDATE dish d JOIN recipe_fix_log l ON l.ref_id = d.id AND l.batch = 'image-url-prod-20261027' AND l.kind = 'dish_cover_url' SET d.cover_url = l.old_value;
-- UPDATE dish_step s JOIN recipe_fix_log l ON l.ref_id = s.id AND l.batch = 'image-url-prod-20261027' AND l.kind = 'dish_step_images' SET s.images = l.old_value;
-- DELETE FROM recipe_fix_log WHERE batch = 'image-url-prod-20261027';
