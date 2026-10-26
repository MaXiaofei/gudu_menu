-- ============================================================
-- V75: 账号与家庭（multi-tenant-family D.1）
-- account            登录账号（凭证 + 平台角色 + 状态），与「家庭成员档案」(member) 分离
-- family             家庭 = 数据隔离单元（租户）
-- family_membership  账号在家庭中的角色（OWNER / ADMIN / MEMBER），UNIQUE(family_id, account_id)
-- family_invitation  邀请码（7 天有效、可撤销、有使用上限）
-- 四张表均为全局表（不按家庭过滤），访问控制在 identity / family 上下文应用层。
-- 幂等：CREATE TABLE IF NOT EXISTS。回滚：sql/rollback/V75-V79_multi_tenant_family.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS account (
  id             BIGINT       PRIMARY KEY AUTO_INCREMENT,
  phone          VARCHAR(32)  NULL COMMENT '登录手机号（历史超管登录名 admin 亦存于此）；注销后置空',
  wx_openid      VARCHAR(64)  NULL COMMENT '微信小程序 openid；合并到手机号账号后随之迁移',
  wx_unionid     VARCHAR(64)  NULL,
  password_hash  VARCHAR(100) NULL COMMENT 'BCrypt；纯微信账号为空',
  nickname       VARCHAR(32)  NULL,
  avatar_url     VARCHAR(255) NULL COMMENT '本站 uploads 地址',
  platform_role  VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER / OPERATOR / SUPER_ADMIN',
  status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DEACTIVATED / MERGED',
  last_family_id BIGINT       NULL COMMENT '上次使用的家庭（登录默认进入）',
  merged_into    BIGINT       NULL COMMENT 'status=MERGED 时并入的账号 id',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deactivated_at DATETIME     NULL,
  UNIQUE KEY uk_account_phone (phone),
  UNIQUE KEY uk_account_openid (wx_openid),
  KEY idx_account_unionid (wx_unionid)
) COMMENT '登录账号（multi-tenant-family）';

CREATE TABLE IF NOT EXISTS family (
  id               BIGINT      PRIMARY KEY AUTO_INCREMENT,
  name             VARCHAR(32) NOT NULL,
  owner_account_id BIGINT      NULL COMMENT '冗余当前主理人，以 family_membership 为准',
  status           VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISSOLVED',
  version          BIGINT      NOT NULL DEFAULT 0 COMMENT '乐观锁',
  created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  dissolved_at     DATETIME    NULL,
  KEY idx_family_owner (owner_account_id)
) COMMENT '家庭（租户）';

CREATE TABLE IF NOT EXISTS family_membership (
  id         BIGINT      PRIMARY KEY AUTO_INCREMENT,
  family_id  BIGINT      NOT NULL,
  account_id BIGINT      NOT NULL,
  role       VARCHAR(16) NOT NULL COMMENT 'OWNER / ADMIN / MEMBER（每个家庭恰好一个 OWNER）',
  joined_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_family_account (family_id, account_id),
  KEY idx_membership_account (account_id)
) COMMENT '家庭成员关系';

CREATE TABLE IF NOT EXISTS family_invitation (
  id         BIGINT      PRIMARY KEY AUTO_INCREMENT,
  family_id  BIGINT      NOT NULL,
  code       VARCHAR(16) NOT NULL,
  created_by BIGINT      NOT NULL COMMENT '邀请人 account.id',
  expires_at DATETIME    NOT NULL,
  max_uses   INT         NOT NULL DEFAULT 10,
  used_count INT         NOT NULL DEFAULT 0,
  revoked    TINYINT     NOT NULL DEFAULT 0,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_invitation_code (code),
  KEY idx_invitation_family (family_id)
) COMMENT '家庭邀请码';
