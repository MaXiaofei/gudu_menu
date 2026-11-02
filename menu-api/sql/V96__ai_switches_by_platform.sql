SET NAMES utf8mb4;
-- ============================================================
-- V96: 生成式 AI 紧急关停开关按端拆分（小程序 .mp / App .app）+ 新增拍照识别、一周餐程两类
-- 键约定：<类别基础键>.<端>，如 agent.chat.enabled.mp。
-- 旧键 agent.chat.enabled / agent.cook_ask.enabled 的当前状态复制到两个端（INSERT IGNORE，已存在的端键不覆盖）；
-- 旧键行保留不删（代码不再读取），日志表不改。新增类别两个端均种子为开。幂等。
-- ============================================================
INSERT IGNORE INTO sys_switch(switch_key, enabled, updated_by, updated_by_name, updated_at, reason)
SELECT CONCAT(switch_key, '.mp'), enabled, updated_by, updated_by_name, updated_at, reason
FROM sys_switch WHERE switch_key IN ('agent.chat.enabled', 'agent.cook_ask.enabled');

INSERT IGNORE INTO sys_switch(switch_key, enabled, updated_by, updated_by_name, updated_at, reason)
SELECT CONCAT(switch_key, '.app'), enabled, updated_by, updated_by_name, updated_at, reason
FROM sys_switch WHERE switch_key IN ('agent.chat.enabled', 'agent.cook_ask.enabled');

-- 万一旧键行不存在（V94 未跑）也保证端键存在，默认开
INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('agent.chat.enabled.mp', 1);
INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('agent.chat.enabled.app', 1);
INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('agent.cook_ask.enabled.mp', 1);
INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('agent.cook_ask.enabled.app', 1);

INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('ai.vision.enabled.mp', 1);
INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('ai.vision.enabled.app', 1);
INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('ai.mealplan.enabled.mp', 1);
INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('ai.mealplan.enabled.app', 1);
