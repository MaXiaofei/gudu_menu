SET NAMES utf8mb4;
-- ============================================================
-- V99: 生成式 AI 紧急关停开关新增两类（营养估算、AI 帮写）× 两端
--   ai.estimate.enabled.<mp|app>   营养估算（/ai/dish/estimate、/ai/nutrition/fill；后台运营维护食材不受限）
--   ai.post_text.enabled.<mp|app>  AI 帮写（/community/drafts/ai-text）
-- 小程序端默认开；App 端默认关（App 暂未上线，防止入口被恶意调用；代码在行缺失时 App 端同样按关）。
-- 不改动已有的 App 行（现网 App 行由运营在管理后台「App 一键关闭全部 AI」操作并留痕）。
-- INSERT IGNORE 以 switch_key 唯一键幂等：重复执行不覆盖运营已改的值。
-- ============================================================
INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('ai.estimate.enabled.mp', 1);
INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('ai.estimate.enabled.app', 0);
INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('ai.post_text.enabled.mp', 1);
INSERT IGNORE INTO sys_switch(switch_key, enabled) VALUES ('ai.post_text.enabled.app', 0);
