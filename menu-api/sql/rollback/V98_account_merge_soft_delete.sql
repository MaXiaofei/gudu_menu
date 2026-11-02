SET NAMES utf8mb4;
-- 回滚 V98（账号合并软删除）。先备份再执行；昵称 / 头像是软删除时清掉的，库内无法还原，需要的话从迁移前备份回填：
--   UPDATE account a JOIN account_backup_v98 b ON b.id = a.id
--   SET a.nickname = b.nickname, a.avatar_url = b.avatar_url, a.last_family_id = b.last_family_id WHERE a.status = 'MERGED';

-- a) 账号恢复可见
UPDATE account SET deleted_at = NULL WHERE status = 'MERGED';

-- b) 恢复被软删的重复档案（改指到目标档案的业务数据不改回：它们本来就是同一个人的数据）
UPDATE member m JOIN account_merge_soft_delete_log l ON l.kind = 'PROFILE' AND l.ref_id = m.id
SET m.deleted = 0;

-- c) 恢复被软解散的空家庭
UPDATE family f JOIN account_merge_soft_delete_log l ON l.kind = 'FAMILY' AND l.ref_id = f.id
SET f.status = 'ACTIVE', f.dissolved_at = NULL;

-- d) 确认代码已回滚后再删列、删记录表
-- ALTER TABLE account DROP INDEX idx_account_deleted_at;
-- ALTER TABLE account DROP COLUMN deleted_at;
-- DROP TABLE account_merge_soft_delete_log;
