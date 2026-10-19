-- ============================================================
-- V71: 厨房圈（showcase-ai-community S3）
-- 身份 = member.id（登录账号；multi-tenant-family 落地后迁移到 account.id）。
-- 计数列由 Redis 增量定时回刷；互动/关注/举报/照着做/打卡均有唯一键保证幂等。
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE。
-- ============================================================
CREATE TABLE IF NOT EXISTS community_post (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  author_id           BIGINT        NOT NULL COMMENT '作者 member.id',
  title               VARCHAR(30)   NOT NULL,
  text                VARCHAR(1000) NOT NULL DEFAULT '',
  images              VARCHAR(2400) NOT NULL COMMENT '图片 JSON 数组（1–9，本站图片）',
  blocked_images      VARCHAR(2400) NULL     COMMENT '被拦截的站外图片 JSON 数组（不展示，供审核查看）',
  dish_id             BIGINT        NULL     COMMENT '关联菜谱',
  visibility          VARCHAR(10)   NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC / FOLLOWERS',
  status              VARCHAR(10)   NOT NULL DEFAULT 'REVIEWING' COMMENT 'PUBLISHED / REVIEWING / HIDDEN / DELETED',
  risk                VARCHAR(8)    NOT NULL DEFAULT 'LOW' COMMENT '预审风险 LOW / MEDIUM / HIGH',
  risk_reasons        VARCHAR(500)  NULL     COMMENT '预审命中原因 JSON 数组',
  risk_scores         VARCHAR(500)  NULL     COMMENT '预审分项得分 JSON 数组 [{label,score}]',
  reviewed            TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已复核（0 = 在审核队列）',
  from_cook_record_id BIGINT        NULL     COMMENT '从食记分享时的 cooking_record.id',
  city                VARCHAR(32)   NULL,
  cover_ratio         DECIMAL(4,2)  NOT NULL DEFAULT 1.00 COMMENT '封面宽高比（瀑布流排版）',
  like_count          INT           NOT NULL DEFAULT 0,
  collect_count       INT           NOT NULL DEFAULT 0,
  comment_count       INT           NOT NULL DEFAULT 0,
  copy_count          INT           NOT NULL DEFAULT 0,
  create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_cpost_feed (status, visibility, create_time),
  INDEX idx_cpost_author (author_id, create_time),
  INDEX idx_cpost_review (reviewed, create_time)
) COMMENT '厨房圈帖子';

CREATE TABLE IF NOT EXISTS community_post_topic (
  post_id  BIGINT NOT NULL,
  topic_id BIGINT NOT NULL,
  PRIMARY KEY (post_id, topic_id),
  INDEX idx_cpt_topic (topic_id, post_id)
) COMMENT '帖子-话题';

CREATE TABLE IF NOT EXISTS community_comment (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id     BIGINT       NOT NULL,
  author_id   BIGINT       NOT NULL,
  parent_id   BIGINT       NULL COMMENT '回复的顶层评论（两级）',
  text        VARCHAR(300) NOT NULL,
  like_count  INT          NOT NULL DEFAULT 0,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ccomment_post (post_id, parent_id, id)
) COMMENT '帖子评论';

CREATE TABLE IF NOT EXISTS community_reaction (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id     BIGINT     NOT NULL,
  user_id     BIGINT     NOT NULL,
  type        VARCHAR(8) NOT NULL COMMENT 'LIKE / COLLECT',
  create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_creaction (post_id, user_id, type),
  INDEX idx_creaction_user (user_id, type)
) COMMENT '赞与收藏';

CREATE TABLE IF NOT EXISTS community_follow (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  follower_id BIGINT   NOT NULL,
  followee_id BIGINT   NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cfollow (follower_id, followee_id),
  INDEX idx_cfollow_followee (followee_id)
) COMMENT '关注关系';

CREATE TABLE IF NOT EXISTS community_copy (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id     BIGINT   NOT NULL,
  user_id     BIGINT   NOT NULL,
  dish_id     BIGINT   NOT NULL COMMENT '复制出的私有菜谱',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ccopy (post_id, user_id),
  INDEX idx_ccopy_post (post_id, create_time)
) COMMENT '照着做（复制到我家）';

CREATE TABLE IF NOT EXISTS community_report (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id     BIGINT       NOT NULL,
  reporter_id BIGINT       NOT NULL,
  reason      VARCHAR(100) NOT NULL,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_creport (post_id, reporter_id),
  INDEX idx_creport_time (create_time)
) COMMENT '举报（同一用户对同一帖只计一次）';

CREATE TABLE IF NOT EXISTS community_topic (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(30)  NOT NULL COMMENT '话题名（不含 #）',
  intro      VARCHAR(200) NOT NULL DEFAULT '',
  rule       VARCHAR(300) NOT NULL DEFAULT '',
  challenge  TINYINT      NOT NULL DEFAULT 0 COMMENT '1 = 周挑战（周一至周日）',
  start_date DATE         NULL,
  end_date   DATE         NULL,
  sort       INT          NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ctopic_name (name)
) COMMENT '话题';

CREATE TABLE IF NOT EXISTS community_topic_checkin (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  topic_id     BIGINT   NOT NULL,
  user_id      BIGINT   NOT NULL,
  checkin_date DATE     NOT NULL,
  post_id      BIGINT   NOT NULL,
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ccheckin (topic_id, user_id, checkin_date),
  INDEX idx_ccheckin_week (topic_id, checkin_date)
) COMMENT '话题挑战打卡（每人每天一次）';

CREATE TABLE IF NOT EXISTS community_moderation_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id     BIGINT       NOT NULL,
  operator_id BIGINT       NOT NULL,
  action      VARCHAR(10)  NOT NULL COMMENT 'APPROVE / HIDE / BAN_7D',
  reason      VARCHAR(50)  NOT NULL,
  note        VARCHAR(300) NULL,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_cmodlog_post (post_id)
) COMMENT '社区审核审计日志';

CREATE TABLE IF NOT EXISTS community_appeal (
  post_id      BIGINT       NOT NULL PRIMARY KEY COMMENT '每帖只能申诉一次',
  author_id    BIGINT       NOT NULL,
  text         VARCHAR(300) NOT NULL,
  handled      TINYINT      NOT NULL DEFAULT 0 COMMENT '1 = 运营已重新处置',
  handled_time DATETIME     NULL,
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_cappeal_pending (handled, create_time)
) COMMENT '被隐藏帖子的作者申诉';

CREATE TABLE IF NOT EXISTS community_ban (
  user_id     BIGINT       NOT NULL PRIMARY KEY,
  until       DATETIME     NOT NULL,
  reason      VARCHAR(100) NOT NULL,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '发帖封禁';

INSERT IGNORE INTO community_topic(name, intro, rule, challenge, start_date, end_date, sort) VALUES
  ('一周不重样', '连续 7 天，每天做一道不重样的家常菜并发布。',
   '每天做一道本周没做过的家常菜，拍照发布并带上话题即打卡；周一至周日打满 7 天得「不重样厨神」徽章，前 10 名上首页推荐。',
   1, '2026-09-28', NULL, 1),
  ('宝宝辅食', '软烂少盐、营养均衡的宝宝餐。', '', 0, NULL, NULL, 2),
  ('快手菜', '下班 20 分钟搞定的家常菜。', '', 0, NULL, NULL, 3);
