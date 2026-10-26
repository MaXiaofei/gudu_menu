-- V91 回滚：notification 收件人两列合回 member_id，family_id 恢复 NOT NULL；撤销迁移登记。
-- 注意：account.AUTO_INCREMENT 不回退（已发出的 >= 1000 万账号 id 不能再复用，回退无意义也不安全）。
-- 回滚后须同时回退应用版本（旧版按 member_id 读写通知，账号 / 档案混用的串号问题会回来）。
-- 不可逆信息：账号级 / 档案级的区分在合并为单列后丢失。

UPDATE notification SET member_id = COALESCE(recipient_account_id, recipient_profile_id) WHERE member_id IS NULL;
UPDATE notification SET family_id = 1 WHERE family_id IS NULL;
DELETE FROM notification WHERE member_id IS NULL;
ALTER TABLE notification MODIFY COLUMN member_id BIGINT NOT NULL;
ALTER TABLE notification MODIFY COLUMN family_id BIGINT NOT NULL COMMENT '所属家庭（租户，multi-tenant-family）';
ALTER TABLE notification DROP INDEX idx_notif_recipient_account;
ALTER TABLE notification DROP INDEX idx_notif_recipient_profile;
ALTER TABLE notification DROP COLUMN recipient_account_id;
ALTER TABLE notification DROP COLUMN recipient_profile_id;
DELETE FROM schema_migration_log WHERE filename = 'V91__id_space_notification_recipient.sql';
