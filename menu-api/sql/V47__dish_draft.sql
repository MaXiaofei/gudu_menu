-- V47: dish_draft 写菜谱草稿表（DESIGN.md §16.4）
-- 背景：写菜谱「存草稿」不校验必填；独立表不污染 dish（搜索/列表/详情无需过滤 status）。
--   用量自由文本原样存 JSON（发布时再解析 amount/unitId）；图片存已上传 URL。
-- 幂等：CREATE TABLE IF NOT EXISTS

CREATE TABLE IF NOT EXISTS dish_draft (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT       NOT NULL COMMENT '归属人（sa-token currentMemberId）',
  name         VARCHAR(64)  NULL COMMENT '菜名（可空 = 未命名草稿）',
  cover_url    VARCHAR(512) NULL,
  prep_time    INT          NULL,
  cook_time    INT          NULL,
  difficulty   INT          NULL,
  note         TEXT         NULL COMMENT '菜谱介绍',
  tags         JSON         NULL COMMENT '标签 dict id 列表',
  ingredients  JSON         NULL COMMENT '[{ingredientId, ingredientName, amountText}] 用量自由文本原样存',
  steps        JSON         NULL COMMENT '[{seq, text, images}]',
  source_type  VARCHAR(16)  NULL COMMENT 'manual / url',
  source_url   VARCHAR(512) NULL COMMENT '导入链接原文',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user_update (user_id, update_time)
) COMMENT='写菜谱草稿（发布后删除）';
