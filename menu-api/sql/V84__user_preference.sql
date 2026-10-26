-- ============================================================
-- V84: 用户偏好（mini-release-compliance A.8，个人信息保护法第 24 条）
-- 以登录账号 member.id 为主键的小表（不改 member 表）；没有行 = 全部默认值。
-- personalized_recommend：1 开启个性化推荐（默认）/ 0 关闭——关闭后智荐与咕嘟首屏只给通用推荐（时令 / 大家常做）。
-- 幂等：CREATE TABLE IF NOT EXISTS。
-- ============================================================
CREATE TABLE IF NOT EXISTS user_preference (
  member_id              BIGINT     NOT NULL PRIMARY KEY COMMENT '登录账号 member.id',
  personalized_recommend TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1 个性化推荐开启 / 0 关闭',
  create_time            DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time            DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '用户偏好';
