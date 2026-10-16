-- ============================================================
-- V67: 意见反馈表（feature-gaps-closure G14）
-- 用户提交（每账号每日 ≤ 10 条，应用层限流）；超管列表 + 标记 NEW/DONE。无唯一约束。
-- 幂等：CREATE TABLE IF NOT EXISTS。
-- ============================================================
CREATE TABLE IF NOT EXISTS feedback (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id   BIGINT        NOT NULL COMMENT '提交人 member.id（登录账号）',
  content     VARCHAR(500)  NOT NULL COMMENT '反馈内容 5–500 字',
  contact     VARCHAR(50)   NULL     COMMENT '联系方式（可空）',
  images      VARCHAR(1024) NULL     COMMENT '图片 JSON 数组（≤3，本站 uploads 地址）',
  client_info VARCHAR(200)  NULL     COMMENT '客户端信息（端/版本/机型）',
  status      VARCHAR(8)    NOT NULL DEFAULT 'NEW' COMMENT 'NEW 待处理 / DONE 已处理',
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_feedback_member_time (member_id, create_time),
  INDEX idx_feedback_status_time (status, create_time)
) COMMENT '意见反馈';
