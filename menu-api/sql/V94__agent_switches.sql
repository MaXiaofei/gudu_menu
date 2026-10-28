SET NAMES utf8mb4;
-- ============================================================
-- V94: 咕嘟助手 / 问咕嘟 紧急关停开关
-- 出现内容风险时由管理后台一键关闭；默认开启（提审版本即线上版本）。
-- 复用 V85 的 sys_switch / sys_switch_log，幂等 INSERT IGNORE。
-- ============================================================
INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('agent.chat.enabled', 1);
INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('agent.cook_ask.enabled', 1);
