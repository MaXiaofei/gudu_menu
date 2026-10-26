-- ============================================================
-- 迁移校验：multi-tenant-family V75–V79（D.5）。只读查询，可反复执行。
-- 期望：每一行的 bad 列为 0（「说明」列描述含义）。任一非 0 → 停止发布，按回滚脚本处理。
-- ============================================================
SELECT 'member.family_id 为空' AS item, COUNT(*) AS bad FROM member WHERE family_id IS NULL
UNION ALL
SELECT 'menu.family_id 为空' AS item, COUNT(*) AS bad FROM menu WHERE family_id IS NULL
UNION ALL
SELECT 'menu_dish.family_id 为空' AS item, COUNT(*) AS bad FROM menu_dish WHERE family_id IS NULL
UNION ALL
SELECT 'menu_prep_status.family_id 为空' AS item, COUNT(*) AS bad FROM menu_prep_status WHERE family_id IS NULL
UNION ALL
SELECT 'menu_activity.family_id 为空' AS item, COUNT(*) AS bad FROM menu_activity WHERE family_id IS NULL
UNION ALL
SELECT 'menu_invite.family_id 为空' AS item, COUNT(*) AS bad FROM menu_invite WHERE family_id IS NULL
UNION ALL
SELECT 'menu_join.family_id 为空' AS item, COUNT(*) AS bad FROM menu_join WHERE family_id IS NULL
UNION ALL
SELECT 'menu_cook_usage.family_id 为空' AS item, COUNT(*) AS bad FROM menu_cook_usage WHERE family_id IS NULL
UNION ALL
SELECT 'menu_template.family_id 为空' AS item, COUNT(*) AS bad FROM menu_template WHERE family_id IS NULL
UNION ALL
SELECT 'ingredient_stock.family_id 为空' AS item, COUNT(*) AS bad FROM ingredient_stock WHERE family_id IS NULL
UNION ALL
SELECT 'pantry.family_id 为空' AS item, COUNT(*) AS bad FROM pantry WHERE family_id IS NULL
UNION ALL
SELECT 'pantry_change_log.family_id 为空' AS item, COUNT(*) AS bad FROM pantry_change_log WHERE family_id IS NULL
UNION ALL
SELECT 'stock_log.family_id 为空' AS item, COUNT(*) AS bad FROM stock_log WHERE family_id IS NULL
UNION ALL
SELECT 'shopping_list.family_id 为空' AS item, COUNT(*) AS bad FROM shopping_list WHERE family_id IS NULL
UNION ALL
SELECT 'shopping_item.family_id 为空' AS item, COUNT(*) AS bad FROM shopping_item WHERE family_id IS NULL
UNION ALL
SELECT 'cooking_record.family_id 为空' AS item, COUNT(*) AS bad FROM cooking_record WHERE family_id IS NULL
UNION ALL
SELECT 'favorite.family_id 为空' AS item, COUNT(*) AS bad FROM favorite WHERE family_id IS NULL
UNION ALL
SELECT 'daily_log.family_id 为空' AS item, COUNT(*) AS bad FROM daily_log WHERE family_id IS NULL
UNION ALL
SELECT 'daily_log_item.family_id 为空' AS item, COUNT(*) AS bad FROM daily_log_item WHERE family_id IS NULL
UNION ALL
SELECT 'review.family_id 为空' AS item, COUNT(*) AS bad FROM review WHERE family_id IS NULL
UNION ALL
SELECT 'review_score.family_id 为空' AS item, COUNT(*) AS bad FROM review_score WHERE family_id IS NULL
UNION ALL
SELECT 'dish_draft.family_id 为空' AS item, COUNT(*) AS bad FROM dish_draft WHERE family_id IS NULL
UNION ALL
SELECT 'dish_history.family_id 为空' AS item, COUNT(*) AS bad FROM dish_history WHERE family_id IS NULL
UNION ALL
SELECT 'meal_plan.family_id 为空' AS item, COUNT(*) AS bad FROM meal_plan WHERE family_id IS NULL
UNION ALL
SELECT 'meal_plan_item.family_id 为空' AS item, COUNT(*) AS bad FROM meal_plan_item WHERE family_id IS NULL
UNION ALL
SELECT 'meal_plan_draft.family_id 为空' AS item, COUNT(*) AS bad FROM meal_plan_draft WHERE family_id IS NULL
UNION ALL
SELECT 'meal_plan_slot.family_id 为空' AS item, COUNT(*) AS bad FROM meal_plan_slot WHERE family_id IS NULL
UNION ALL
SELECT 'notification.family_id 为空' AS item, COUNT(*) AS bad FROM notification WHERE family_id IS NULL
UNION ALL
SELECT 'family 未解散但 OWNER 数 ≠ 1' AS item, COUNT(*) AS bad FROM family f WHERE f.status = 'ACTIVE' AND (SELECT COUNT(*) FROM family_membership m WHERE m.family_id = f.id AND m.role = 'OWNER') <> 1
UNION ALL
SELECT 'family.owner_account_id 与 OWNER 成员不一致' AS item, COUNT(*) AS bad FROM family f WHERE f.status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM family_membership m WHERE m.family_id = f.id AND m.role = 'OWNER' AND m.account_id = f.owner_account_id)
UNION ALL
SELECT '有凭证的存量 member 未生成 account' AS item, COUNT(*) AS bad FROM member m WHERE m.deleted = 0 AND (m.password_hash IS NOT NULL OR m.openid IS NOT NULL) AND NOT EXISTS (SELECT 1 FROM account a WHERE a.id = m.id)
UNION ALL
SELECT 'account 没有任何家庭（ACTIVE）' AS item, COUNT(*) AS bad FROM account a WHERE a.status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM family_membership m JOIN family f ON f.id = m.family_id AND f.status = 'ACTIVE' WHERE m.account_id = a.id)
UNION ALL
SELECT '成员关系缺关联档案' AS item, COUNT(*) AS bad FROM family_membership fm JOIN family f ON f.id = fm.family_id AND f.status = 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM member p WHERE p.family_id = fm.family_id AND p.account_id = fm.account_id AND p.deleted = 0)
UNION ALL
SELECT 'account 手机号仍为 wx_ 占位' AS item, COUNT(*) AS bad FROM account WHERE phone LIKE 'wx\\_%'
UNION ALL
SELECT '超管账号数为 0' AS item, IF(COUNT(*) = 0, 1, 0) AS bad FROM account WHERE platform_role = 'SUPER_ADMIN'
UNION ALL
SELECT 'dish 指向不存在的家庭' AS item, COUNT(*) AS bad FROM dish d WHERE d.family_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM family f WHERE f.id = d.family_id)
UNION ALL
SELECT 'ingredient_stock 同家庭同食材重复' AS item, COUNT(*) AS bad FROM (SELECT family_id, ingredient_id FROM ingredient_stock GROUP BY family_id, ingredient_id HAVING COUNT(*) > 1) x;

-- ------------------------------------------------------------
-- 信息项（不要求为 0，人工核对）
-- ------------------------------------------------------------
SELECT 'account 总数' AS item, COUNT(*) AS n FROM account
UNION ALL SELECT '默认家庭（id=1）成员数', COUNT(*) FROM family_membership WHERE family_id = 1
UNION ALL SELECT '微信自动注册账号独立建家数', COUNT(*) FROM family WHERE id > 2 AND name = '我的家'
UNION ALL SELECT '公共菜谱数（dish.family_id IS NULL）', COUNT(*) FROM dish WHERE family_id IS NULL AND deleted = 0;

-- E.8：疑似「同一人两个账号」（同昵称的微信账号与手机号账号）——只列出，不自动合并（无法证明是同一人），
-- 由用户在小程序「绑定手机号账号」主动发起 /account/merge-phone。
SELECT w.id AS wechat_account, p.id AS phone_account, w.nickname
FROM account w JOIN account p ON p.nickname = w.nickname AND p.id <> w.id
WHERE w.wx_openid IS NOT NULL AND w.phone IS NULL AND p.phone IS NOT NULL AND p.wx_openid IS NULL
  AND w.status = 'ACTIVE' AND p.status = 'ACTIVE' AND w.nickname <> '微信用户';
