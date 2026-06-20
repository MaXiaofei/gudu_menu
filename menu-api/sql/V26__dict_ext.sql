-- ============================================================
-- V26__dict_ext.sql
-- 烟火小食单：家庭成员加性别 + 适用人群扩充
--   1) sys_dict 新增 group=gender：男 / 女（表单单选用）
--   2) audience 适用人群扩充：
--      原 高血压/高血糖/高血脂/宝宝辅食 保留（宝宝辅食作场景，用户可不用作人群）
--      新增 普通人/青少年/儿童/孕妇/乳母/老年人/健身/素食/减脂期/增肌期
-- 幂等：INSERT...SELECT WHERE NOT EXISTS(uk_group_name 兜底)；
--      绝不 DELETE，避免破坏已有 member.health_profile.audiences 引用。
-- UTF-8
-- ============================================================

START TRANSACTION;

-- 1) 性别字典 group=gender：男 / 女
INSERT INTO sys_dict(dict_group, name, sort)
SELECT 'gender', v.name, v.sort
FROM (
  VALUES
  ROW('男', 1),
  ROW('女', 2)
) AS v(name, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict d WHERE d.dict_group = 'gender' AND d.name = v.name
);

-- 2) audience 适用人群扩充（按 group+name 去重；保留现有 高血压/高血糖/高血脂/宝宝辅食）
INSERT INTO sys_dict(dict_group, name, sort)
SELECT 'audience', v.name, v.sort
FROM (
  VALUES
  ROW('普通人',   10),
  ROW('青少年',   20),
  ROW('儿童',     30),
  ROW('孕妇',     40),
  ROW('乳母',     50),
  ROW('老年人',   60),
  ROW('健身',     70),
  ROW('素食',     80),
  ROW('减脂期',   90),
  ROW('增肌期',  100)
) AS v(name, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict d WHERE d.dict_group = 'audience' AND d.name = v.name
);

COMMIT;
