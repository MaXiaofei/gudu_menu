-- ============================================================
-- V72: 拍照识别任务 + 一周餐程草稿（showcase-ai-community §4 §5）
-- vision_job：识别任务（PENDING/DONE/FAILED），原图 24h 清理（image_url 置空）、记录 7 天删除。
-- meal_plan_draft / meal_plan_slot：一周（周一至周日）餐程草稿与格子；确认后 status=APPLIED。
-- 无唯一约束。幂等：CREATE TABLE IF NOT EXISTS。
-- ============================================================
CREATE TABLE IF NOT EXISTS vision_job (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id   BIGINT        NOT NULL COMMENT '发起人 member.id（登录账号）',
  mode        VARCHAR(16)   NOT NULL COMMENT 'FRIDGE 冰箱 / RECEIPT 小票 / RECIPE 菜谱截图',
  image_url   VARCHAR(255)  NULL     COMMENT '本站上传原图地址；24 小时后清理置空',
  status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / DONE / FAILED',
  error       VARCHAR(200)  NULL     COMMENT '失败原因（给用户看）',
  items_json  MEDIUMTEXT    NULL     COMMENT '识别项 JSON（已对齐食材字典、分级）',
  recipe_json TEXT          NULL     COMMENT 'RECIPE 模式：标题/用料/步骤 JSON',
  applied_at  DATETIME      NULL     COMMENT '放进余量时间（只能一次）',
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_vision_job_member (member_id, create_time),
  INDEX idx_vision_job_create (create_time)
) COMMENT '拍照识别任务';

CREATE TABLE IF NOT EXISTS meal_plan_draft (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id        BIGINT        NOT NULL COMMENT '创建人 member.id',
  week_start       DATE          NOT NULL COMMENT '周一',
  week_end         DATE          NOT NULL COMMENT '周日',
  request_json     TEXT          NOT NULL COMMENT '生成约束（餐次/人份/成员/工作日时长/预算/优先快到期）',
  constraints_json TEXT          NULL     COMMENT '约束标签（展示用）',
  check_json       TEXT          NULL     COMMENT '校验结论（passed/rounds/notes）',
  status           VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / APPLIED',
  applied_menu_ids VARCHAR(255)  NULL     COMMENT '生成的食集 id，逗号分隔',
  create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_meal_plan_draft_member (member_id, create_time)
) COMMENT '一周餐程草稿';

CREATE TABLE IF NOT EXISTS meal_plan_slot (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  draft_id  BIGINT       NOT NULL COMMENT 'meal_plan_draft.id',
  plan_date DATE         NOT NULL,
  meal      VARCHAR(16)  NOT NULL COMMENT 'LUNCH / DINNER',
  dish_id   BIGINT       NOT NULL,
  badges    VARCHAR(64)  NULL     COMMENT 'EXPIRING,BABY',
  INDEX idx_meal_plan_slot_draft (draft_id)
) COMMENT '一周餐程格子';
