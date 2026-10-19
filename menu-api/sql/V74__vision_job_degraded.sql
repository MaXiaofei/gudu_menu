-- ============================================================
-- V74: vision_job 加 degraded（showcase-ai-community 集成：识别接入模型网关）
-- 视觉模型不可用时退回示例识别 / 后备模型，结果标记 degraded=1，客户端提示用户核对。
-- 幂等：列存在则跳过。
-- ============================================================
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vision_job' AND COLUMN_NAME = 'degraded');
SET @s := IF(@c = 0,
  'ALTER TABLE vision_job ADD COLUMN degraded TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''1=示例识别或后备模型结果'' AFTER recipe_json',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
