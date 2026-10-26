-- ============================================================
-- V80: 图片内容安全异步检测记录（mini-release-compliance A.6）
-- 上传后提交微信 mediaCheckAsync，记 trace_id → 图片地址；消息推送回调按 trace_id 定论，
-- 违规（RISKY）时文件移入隔离目录。头像保存前按 media_url 查最近一次检测（违规拒绝 / 提交失败重提）。
-- trace_id 唯一（提交失败的记录 trace_id 为 NULL，MySQL 唯一索引允许多个 NULL）。
-- 幂等：CREATE TABLE IF NOT EXISTS。
-- ============================================================
CREATE TABLE IF NOT EXISTS content_media_check (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  trace_id    VARCHAR(64)  NULL     COMMENT '微信 mediaCheckAsync 返回的 trace_id（提交失败为 NULL）',
  media_url   VARCHAR(255) NOT NULL COMMENT '本站上传地址 /gudu/uploads/…',
  member_id   BIGINT       NULL     COMMENT '上传账号 member.id',
  purpose     VARCHAR(16)  NOT NULL COMMENT '用途 AVATAR / POST / UPLOAD …',
  status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PASS / REVIEW / RISKY / SUBMIT_FAILED / CHECK_FAILED',
  label       INT          NOT NULL DEFAULT 0 COMMENT '微信命中标签（100 正常，20002 色情 …）',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_content_media_check_trace (trace_id),
  INDEX idx_content_media_check_url (media_url)
) COMMENT '图片内容安全检测';
