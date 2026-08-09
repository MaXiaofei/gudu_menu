-- V45: 食集聚餐（邀请 + 成员心跳 + 加菜/删菜留痕 + 自定义菜名）
-- 背景：docs/pantry-shopping-redesign.md §8（v0.8：H5 过渡方案，guest_key 身份）
-- 房主 = menu_invite.created_by（邀请生成人）；menu 表无 owner 字段，MVP 不做房主特权

-- 1) menu_dish 改列：dish_id 可空（自定义菜名）+ custom_name + 谁加的
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='menu_dish' AND COLUMN_NAME='custom_name');
SET @s1 := IF(@c1=0,'ALTER TABLE menu_dish ADD COLUMN custom_name VARCHAR(64) NULL COMMENT ''自定义菜名（dish_id 为空时用）'' AFTER dish_id','SELECT 1');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='menu_dish' AND COLUMN_NAME='added_by_member_id');
SET @s2 := IF(@c2=0,'ALTER TABLE menu_dish ADD COLUMN added_by_member_id BIGINT NULL COMMENT ''谁加的（null=房主；朋友=其 member_id）'' AFTER custom_name','SELECT 1');
PREPARE p2 FROM @s2; EXECUTE p2; DEALLOCATE PREPARE p2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='menu_dish' AND COLUMN_NAME='added_by_nickname');
SET @s3 := IF(@c3=0,'ALTER TABLE menu_dish ADD COLUMN added_by_nickname VARCHAR(32) NULL COMMENT ''冗余昵称（房主加的不填）'' AFTER added_by_member_id','SELECT 1');
PREPARE p3 FROM @s3; EXECUTE p3; DEALLOCATE PREPARE p3;

-- dish_id 改可空（MySQL 8 支持 MODIFY）
SET @s4 := 'ALTER TABLE menu_dish MODIFY COLUMN dish_id BIGINT NULL COMMENT ''菜品（自定义菜名时为空）''';
PREPARE p4 FROM @s4; EXECUTE p4; DEALLOCATE PREPARE p4;

-- 2) 邀请凭证：一食集一邀请（刷新即换 code/token）
CREATE TABLE IF NOT EXISTS menu_invite (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  menu_id    BIGINT NOT NULL,
  code       VARCHAR(8) NOT NULL COMMENT '6 位口令（短码分享）',
  token      VARCHAR(64) NOT NULL COMMENT '深链 token（二维码/链接，指向 H5 together.html?token=）',
  created_by BIGINT NOT NULL COMMENT '房主 member_id（邀请生成人）',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_invite_code (code),
  UNIQUE KEY uk_invite_token (token),
  UNIQUE KEY uk_invite_menu (menu_id)
) COMMENT '食集聚餐邀请凭证（一食集一邀请）';

-- 3) 成员 + 轮询心跳（H5 访客走 guest_key）
CREATE TABLE IF NOT EXISTS menu_join (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  menu_id       BIGINT NOT NULL,
  member_id     BIGINT NULL COMMENT '登录用户（H5 访客为空）',
  guest_key     VARCHAR(64) NULL COMMENT 'H5 访客凭证（唯一）',
  nickname      VARCHAR(32) NOT NULL,
  last_active_at DATETIME NOT NULL COMMENT '轮询时更新（心跳）',
  UNIQUE KEY uk_menu_member (menu_id, member_id),
  UNIQUE KEY uk_guest (guest_key)
) COMMENT '聚餐成员（轮询即心跳）';

-- 4) 活动流：加菜/删菜留痕（谁删的）
CREATE TABLE IF NOT EXISTS menu_activity (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  menu_id     BIGINT NOT NULL,
  member_id   BIGINT NULL,
  nickname    VARCHAR(32) NULL,
  action      VARCHAR(16) NOT NULL COMMENT 'add 点菜 / remove 删菜',
  dish_id     BIGINT NULL,
  dish_name   VARCHAR(64) NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_activity_menu (menu_id, create_time DESC)
) COMMENT '聚餐活动流（记录谁加的/谁删的）';
