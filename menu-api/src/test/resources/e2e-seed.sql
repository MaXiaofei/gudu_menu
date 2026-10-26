-- E2E 种子：每个 @Test 方法前执行（@Sql BEFORE_TEST_METHOD）。
-- 静态种子(user/sys_dict/nutrition_metric/ingredient/ingredient_nutrition/dish/dish_ingredient/menu_template)
--   由 V01-V23 已灌，不在此重置。只清理动态业务表，保证用例间数据隔离。
-- 逻辑删除字段 deleted 的表用物理删（DELETE）彻底清，避免 MP 逻辑删除残留干扰。
--
-- 演示家庭（scripts/seed/demo-family.mjs，recipe-content-seed 5.x）保留不清：
--   成员 phone LIKE 'demo%'；其食集 = menu.target_member_id 指向演示成员；
--   做饭记录/收藏/通知按 member_id 归属。E2E 用例只读写自己的账号与新建数据，不受影响。
--   （NOT IN 前置 IS NULL 判断：NULL NOT IN (...) 为 NULL，不加会漏删）
DELETE FROM shopping_item WHERE list_id NOT IN (
  SELECT sl.id FROM shopping_list sl JOIN menu m ON m.id = sl.source_menu_id
  JOIN member mb ON mb.id = m.target_member_id WHERE mb.phone LIKE 'demo%');
DELETE FROM shopping_list WHERE source_menu_id IS NULL OR source_menu_id NOT IN (
  SELECT m.id FROM menu m JOIN member mb ON mb.id = m.target_member_id WHERE mb.phone LIKE 'demo%');
DELETE FROM daily_log_item;
DELETE FROM daily_log;
DELETE FROM notification WHERE member_id IS NULL
  OR member_id NOT IN (SELECT id FROM member WHERE phone LIKE 'demo%');
DELETE FROM pantry_change_log;
DELETE FROM pantry;
DELETE FROM review;
DELETE FROM review_score;
DELETE FROM cooking_record WHERE member_id IS NULL
  OR member_id NOT IN (SELECT id FROM member WHERE phone LIKE 'demo%');
DELETE FROM favorite WHERE member_id IS NULL
  OR member_id NOT IN (SELECT id FROM member WHERE phone LIKE 'demo%');
DELETE FROM menu_dish WHERE menu_id NOT IN (
  SELECT m.id FROM menu m JOIN member mb ON mb.id = m.target_member_id WHERE mb.phone LIKE 'demo%');
DELETE FROM menu WHERE target_member_id IS NULL
  OR target_member_id NOT IN (SELECT id FROM member WHERE phone LIKE 'demo%');
DELETE FROM dish_history;
DELETE FROM ai_call_log;
-- V67 意见反馈（每账号每日 ≤10 条限流，E2E 重复跑必须清）
DELETE FROM feedback;
-- V80 图片内容安全检测（trace_id 唯一约束；E2E 自建记录 trace_id 用 E2E 前缀、地址带 /e2e/）
DELETE FROM content_media_check WHERE trace_id LIKE 'E2E%' OR media_url LIKE '%/e2e/%';
-- V85 厨房圈紧急关停开关：E2E 会关再开，兜底恢复为开（防中途失败把 staging 厨房圈关着），清 E2E 日志
INSERT INTO sys_switch(switch_key, enabled) VALUES ('community.enabled', 1) ON DUPLICATE KEY UPDATE enabled = 1;
DELETE FROM sys_switch_log WHERE reason LIKE 'E2E%';

-- ============================================================
-- 静态种子自愈：把「番茄/鸡蛋」规范化到测试常量依赖的固定 id(1/2)，
-- 并修复 dish=1(番茄炒蛋) 的 dish_ingredient 指向。
--
-- 背景：V23 按 name 幂等 DELETE+INSERT 食材，会把 V14 demo 的
--   番茄(id=1)/鸡蛋(id=2) 删掉后重插到新自增 id(如 63/138)，
--   而 dish_ingredient(dish=1) 仍引用旧 id(1/2/16/17/18)，形成孤儿
--   → 营养汇总/采购展开/AI 落库全取不到食材，E2E 三场景失败。
--   本块每次 @Test 前幂等修复，可重复执行、对已规范状态无副作用。
-- 幂等要点：漂移行（同名、id 不是 1/2）先改名让位，其菜谱引用迁到固定 id 后再删；
--   固定 id 已是番茄/鸡蛋时原地保留。不再按名字清空全库菜谱的番茄/鸡蛋关联——
--   旧写法每个 @Test 都会删掉公共菜谱库里所有含番茄/鸡蛋的 dish_ingredient
--   （recipe-content-seed 导入的 300+ 道菜因此丢关联，覆盖率/智荐失真）。
-- ============================================================

-- ---- 番茄 -> id=1 ----
-- 固定 id=1 若被别的食材占用：连同其营养/菜谱关联清掉（与旧语义一致）
DELETE inn FROM ingredient_nutrition inn JOIN ingredient i ON i.id = inn.ingredient_id
  WHERE i.id = 1 AND i.name <> '番茄';
DELETE di FROM dish_ingredient di JOIN ingredient i ON i.id = di.ingredient_id
  WHERE i.id = 1 AND i.name <> '番茄';
DELETE FROM ingredient WHERE id = 1 AND name <> '番茄';
-- 漂移番茄（同名、id≠1）改名让位
UPDATE ingredient SET name = CONCAT('__drift_tomato_', id) WHERE name = '番茄' AND id <> 1;
-- 品类按名取（预发库字典 id 曾重排，按名兜底，取不到回退 24）
INSERT INTO ingredient(id, name, icon_key, purchase_category_id, purchase_count, usage_count, deleted)
  VALUES (1, '番茄', 'tomato', COALESCE((SELECT d.id FROM sys_dict d WHERE d.dict_group = 'purchase_category'
    AND d.name = '蔬菜' ORDER BY d.id LIMIT 1), 24), 0, 0, 0)
  ON DUPLICATE KEY UPDATE name = '番茄', icon_key = 'tomato', purchase_category_id = VALUES(purchase_category_id), deleted = 0;
-- 漂移行的菜谱引用迁到 id=1（同菜已有 id=1 的重复行由 IGNORE 跳过，随后删掉）
UPDATE IGNORE dish_ingredient di JOIN ingredient i ON i.id = di.ingredient_id
  SET di.ingredient_id = 1 WHERE i.name LIKE '\_\_drift\_tomato\_%';
DELETE di  FROM dish_ingredient di JOIN ingredient i ON i.id = di.ingredient_id WHERE i.name LIKE '\_\_drift\_tomato\_%';
DELETE inn FROM ingredient_nutrition inn JOIN ingredient i ON i.id = inn.ingredient_id WHERE i.name LIKE '\_\_drift\_tomato\_%';
DELETE s   FROM ingredient_stock s JOIN ingredient i ON i.id = s.ingredient_id WHERE i.name LIKE '\_\_drift\_tomato\_%';
DELETE FROM ingredient WHERE name LIKE '\_\_drift\_tomato\_%';
DELETE FROM ingredient_nutrition WHERE ingredient_id = 1;
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) VALUES
  (1, 1, 19), (1, 2, 0.9), (1, 3, 0.2), (1, 4, 4.0), (1, 5, 2.6), (1, 6, 30);

-- ---- 鸡蛋 -> id=2 ----
-- 固定 id=2 若被别的食材占用：连同其营养/菜谱关联清掉（与旧语义一致）
DELETE inn FROM ingredient_nutrition inn JOIN ingredient i ON i.id = inn.ingredient_id
  WHERE i.id = 2 AND i.name <> '鸡蛋';
DELETE di FROM dish_ingredient di JOIN ingredient i ON i.id = di.ingredient_id
  WHERE i.id = 2 AND i.name <> '鸡蛋';
DELETE FROM ingredient WHERE id = 2 AND name <> '鸡蛋';
-- 漂移鸡蛋（同名、id≠2）改名让位
UPDATE ingredient SET name = CONCAT('__drift_egg_', id) WHERE name = '鸡蛋' AND id <> 2;
-- 品类按名取（预发库字典 id 曾重排，按名兜底，取不到回退 27）
INSERT INTO ingredient(id, name, icon_key, purchase_category_id, purchase_count, usage_count, deleted)
  VALUES (2, '鸡蛋', 'egg', COALESCE((SELECT d.id FROM sys_dict d WHERE d.dict_group = 'purchase_category'
    AND d.name = '蛋类' ORDER BY d.id LIMIT 1), 27), 0, 0, 0)
  ON DUPLICATE KEY UPDATE name = '鸡蛋', icon_key = 'egg', purchase_category_id = VALUES(purchase_category_id), deleted = 0;
-- 漂移行的菜谱引用迁到 id=2（同菜已有 id=2 的重复行由 IGNORE 跳过，随后删掉）
UPDATE IGNORE dish_ingredient di JOIN ingredient i ON i.id = di.ingredient_id
  SET di.ingredient_id = 2 WHERE i.name LIKE '\_\_drift\_egg\_%';
DELETE di  FROM dish_ingredient di JOIN ingredient i ON i.id = di.ingredient_id WHERE i.name LIKE '\_\_drift\_egg\_%';
DELETE inn FROM ingredient_nutrition inn JOIN ingredient i ON i.id = inn.ingredient_id WHERE i.name LIKE '\_\_drift\_egg\_%';
DELETE s   FROM ingredient_stock s JOIN ingredient i ON i.id = s.ingredient_id WHERE i.name LIKE '\_\_drift\_egg\_%';
DELETE FROM ingredient WHERE name LIKE '\_\_drift\_egg\_%';
DELETE FROM ingredient_nutrition WHERE ingredient_id = 2;
INSERT INTO ingredient_nutrition(ingredient_id, metric_id, value) VALUES
  (2, 1, 144), (2, 2, 13.3), (2, 3, 8.8), (2, 4, 2.8), (2, 5, 1.5), (2, 6, 30);

-- ---- dish=1(番茄炒蛋) 自愈：预发库数据漂移曾删掉 V14 demo 菜 id=1，致依赖番茄炒蛋的用例全挂。
--      幂等 upsert（只复位 id=1 这一行的名称/删除标记，不动其他字段）。
INSERT INTO dish(id, name, prep_time, cook_time, difficulty, source, deleted)
VALUES (1, '番茄炒蛋', 5, 10, 1, 'ORIGINAL', 0)
ON DUPLICATE KEY UPDATE name = '番茄炒蛋', deleted = 0;

-- ---- dish=1(番茄炒蛋) 食材挂载：清掉全部（含孤儿 16/17/18），挂 番茄(300g)+鸡蛋(180g) ----
-- grams 列回填（V45 引入 grams 为聚合基准，NeedAggregator 跳过 grams=NULL 的行；
--   旧种子只插 amount 致 cook/summary 聚合为空）。单位 g(id=20)，amount=grams。
DELETE FROM dish_ingredient WHERE dish_id = 1;
INSERT INTO dish_ingredient(dish_id, ingredient_id, amount, unit_id, grams) VALUES
  (1, 1, 300.00, 20, 300.00), (1, 2, 180.00, 20, 180.00);

-- ---- 兜底：清 ingredient_nutrition 里任何 ingredient 已不存在的孤儿行 ----
DELETE inn FROM ingredient_nutrition inn LEFT JOIN ingredient i ON i.id = inn.ingredient_id WHERE i.id IS NULL;

-- 重置自增，便于断言固定 id（可选；测试用返回 id 不依赖固定值，故保留默认即可）。

-- ============================================================
-- V29 合并兜底：给 member 1(张爸爸/掌勺) 补登录账号 phone='13800000001' / 密码 'chef123'
-- 用于「手机号登录」E2E 场景。每测试前幂等重置（测试库专用，覆盖安全）。
-- BCrypt('chef123') 固定哈希，避免每条测试重新 encode。
-- ============================================================
UPDATE member SET phone = '13800000001',
  password_hash = '$2a$10$8fMxXFI3W4XzBun3fNpUIuXT4dN9CRvHcw6K7edMJU78705ETwVrK'
WHERE id = 1;

-- ============================================================
-- 超管账号自愈（loginAdmin 用 admin / admin123）：预发库数据漂移曾把 phone='admin' 的账号删掉，
-- 致全部 E2E 登录失败。按 V29 兜底同款 BCrypt(admin123) 幂等补建/复位（只动 phone='admin' 这一行）。
-- ============================================================
INSERT INTO member(name, phone, password_hash, is_admin, role_tags, health_profile)
SELECT '掌勺人', 'admin', '$2a$10$UM6Ql8.rTKA1aGBSup2ACu4ol4WikGIlfyAPPSciymgdPMpWqa/Ba', 1, '32', '{}'
WHERE NOT EXISTS (SELECT 1 FROM member m WHERE m.phone = 'admin');
UPDATE member SET is_admin = 1, deleted = 0,
  password_hash = '$2a$10$UM6Ql8.rTKA1aGBSup2ACu4ol4WikGIlfyAPPSciymgdPMpWqa/Ba'
WHERE phone = 'admin';

-- ============================================================
-- 临期库存种子（通知_录临期库存触发扫描 / 临期查询 / 首页智荐 场景用）。
-- V64 起临期数据源 = 三档库存 ingredient_stock.best_before（旧 pantry 批次表已迁移冻结，上面已清空）。
-- 番茄（id=1）建档 ENOUGH + 明天到期（CURDATE 动态，永远落在 3 天临期窗口内）；幂等 upsert。
-- ============================================================
INSERT INTO ingredient_stock(ingredient_id, level, best_before, opened_at)
VALUES (1, 'ENOUGH', CURDATE() + INTERVAL 1 DAY, NULL)
ON DUPLICATE KEY UPDATE level = 'ENOUGH', best_before = CURDATE() + INTERVAL 1 DAY, opened_at = NULL;
-- 鸡蛋（id=2）建档 ENOUGH、无保质期（单菜直做「用了一些」降档断言用）
INSERT INTO ingredient_stock(ingredient_id, level, best_before, opened_at)
VALUES (2, 'ENOUGH', NULL, NULL)
ON DUPLICATE KEY UPDATE level = 'ENOUGH', best_before = NULL, opened_at = NULL;

-- ============================================================
-- E2E 自建数据兜底清理（GuduE2ECoverageTest 可重复跑）：
-- 动态表上面已清，但 sys_dict/member 等静态表不在清理范围；
-- Coverage 测试自建的字典/成员有唯一约束（uk_group_name / phone），
-- 残留会导致下次运行 Duplicate 冲突 → 每测试前按名字/前缀清掉。
-- ============================================================
DELETE FROM sys_dict WHERE name LIKE 'E2E%';
DELETE FROM sys_dict WHERE name LIKE 'e2e%';
DELETE FROM member WHERE phone LIKE '1390000%';
DELETE FROM member WHERE name LIKE 'E2E%';
-- E2E 自建菜（连同步骤/用料/字典关联先清孤儿）
DELETE di FROM dish_ingredient di LEFT JOIN dish d ON d.id = di.dish_id
  WHERE d.id IS NULL OR d.name LIKE 'E2E%';
DELETE ds FROM dish_step ds LEFT JOIN dish d ON d.id = ds.dish_id
  WHERE d.id IS NULL OR d.name LIKE 'E2E%';
DELETE dd FROM dish_dict dd LEFT JOIN dish d ON d.id = dd.dish_id
  WHERE d.id IS NULL OR d.name LIKE 'E2E%';
DELETE FROM dish WHERE name LIKE 'E2E%';
DELETE FROM dish_draft WHERE name LIKE 'E2E%';
DELETE FROM menu WHERE name LIKE 'E2E%';
DELETE FROM nutrition_metric WHERE name LIKE 'e2e%';
DELETE inn FROM ingredient_nutrition inn
  LEFT JOIN ingredient i ON i.id = inn.ingredient_id
  WHERE i.id IS NULL OR i.name LIKE 'E2E%';
DELETE FROM ingredient WHERE name LIKE 'E2E%';

-- ============================================================
-- [community] 厨房圈（V71）E2E 兜底清理（GuduE2ECommunityTest 可重复跑）：
-- E2E 帖子标题统一 'E2E' 前缀；E2E 成员（phone 1390000%）上面已删，这里按孤儿清理其关系行。
-- 唯一约束表（reaction / follow / copy / report / checkin / appeal / ban）全部覆盖。
-- =====================================================

-- ============================================================
DELETE FROM community_post WHERE title LIKE 'E2E%';
DELETE pt FROM community_post_topic pt LEFT JOIN community_post p ON p.id = pt.post_id WHERE p.id IS NULL;
DELETE c FROM community_comment c LEFT JOIN community_post p ON p.id = c.post_id WHERE p.id IS NULL;
DELETE r FROM community_reaction r LEFT JOIN community_post p ON p.id = r.post_id WHERE p.id IS NULL;
DELETE cp FROM community_copy cp LEFT JOIN community_post p ON p.id = cp.post_id WHERE p.id IS NULL;
DELETE rp FROM community_report rp LEFT JOIN community_post p ON p.id = rp.post_id WHERE p.id IS NULL;
DELETE ck FROM community_topic_checkin ck LEFT JOIN community_post p ON p.id = ck.post_id WHERE p.id IS NULL;
DELETE ap FROM community_appeal ap LEFT JOIN community_post p ON p.id = ap.post_id WHERE p.id IS NULL;
DELETE ml FROM community_moderation_log ml LEFT JOIN community_post p ON p.id = ml.post_id WHERE p.id IS NULL;
DELETE f FROM community_follow f LEFT JOIN member a ON a.id = f.follower_id LEFT JOIN member b ON b.id = f.followee_id
  WHERE a.id IS NULL OR b.id IS NULL;
DELETE bn FROM community_ban bn LEFT JOIN member m ON m.id = bn.user_id WHERE m.id IS NULL;

-- ============================================================
-- [vision/mealplan] V72 拍照识别 + 一周餐程（GuduE2EVisionMealPlanTest 可重复跑）
-- 演示成员（phone LIKE 'demo%'）的识别任务与餐程保留；其余（E2E 账号）清掉。
-- E2E 识别入库只用「E2E视觉」前缀的新食材，余量/流水按孤儿清（食材已被上面按 E2E% 删除）。
-- 餐程确认生成的食集 target_member_id = 发起人（非演示成员），已被上面的 menu 清理覆盖。
-- ============================================================
DELETE FROM vision_job WHERE member_id IS NULL
  OR member_id NOT IN (SELECT id FROM member WHERE phone LIKE 'demo%');
DELETE FROM meal_plan_slot WHERE draft_id NOT IN (
  SELECT d.id FROM meal_plan_draft d JOIN member mb ON mb.id = d.member_id WHERE mb.phone LIKE 'demo%');
DELETE FROM meal_plan_draft WHERE member_id IS NULL
  OR member_id NOT IN (SELECT id FROM member WHERE phone LIKE 'demo%');
DELETE s FROM ingredient_stock s LEFT JOIN ingredient i ON i.id = s.ingredient_id
  WHERE i.id IS NULL OR i.name LIKE 'E2E%';
DELETE l FROM stock_log l LEFT JOIN ingredient i ON i.id = l.ingredient_id
  WHERE i.id IS NULL OR i.name LIKE 'E2E%';
-- ============================================================
-- [agent] 咕嘟 Agent / 模型网关（V70 / V73）E2E 自建数据清理：
-- 会话标题、评测用例 id、评测运行版本号、网关供应商名均以 E2E 开头；动作在 Redis（5 分钟 TTL）无需清。
-- 表不存在时（V70/V73 未执行）以下语句会报错 → 先执行迁移再跑 E2E。
-- ============================================================
DELETE c FROM agent_eval_case c JOIN agent_trace t ON t.id = c.from_trace_id
  JOIN agent_session s ON s.id = t.session_id WHERE s.title LIKE 'E2E%';
DELETE sp FROM agent_span sp JOIN agent_trace t ON t.id = sp.trace_id
  JOIN agent_session s ON s.id = t.session_id WHERE s.title LIKE 'E2E%';
DELETE t FROM agent_trace t JOIN agent_session s ON s.id = t.session_id WHERE s.title LIKE 'E2E%';
DELETE m FROM agent_message m JOIN agent_session s ON s.id = m.session_id WHERE s.title LIKE 'E2E%';
DELETE FROM agent_session WHERE title LIKE 'E2E%';
DELETE FROM agent_eval_case WHERE case_id LIKE 'E2E%';
DELETE FROM agent_eval_run WHERE prompt_version LIKE 'E2E%';
DELETE pm FROM ai_provider_model pm JOIN ai_provider p ON p.id = pm.provider_id WHERE p.name LIKE 'E2E%';
DELETE FROM ai_provider WHERE name LIKE 'E2E%';
-- 咕嘟越权用例专用的非管理员登录账号（密码 chef123；上面已按 phone LIKE '1390000%' 清理，这里幂等重建）
INSERT INTO member(name, phone, password_hash, is_admin, role_tags, health_profile, deleted)
VALUES ('E2E咕嘟访客', '13900000088', '$2a$10$8fMxXFI3W4XzBun3fNpUIuXT4dN9CRvHcw6K7edMJU78705ETwVrK', 0, '34', '{}', 0);
