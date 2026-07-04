-- ============================================================
-- V37 备菜模块：menu_prep_status（食集备料状态：待备/已备/化冻/腌制）
-- 背景：docs/redesign-audit.md §1.3 gap ④（备菜模块未落地）
-- plan：docs/superpowers/plans/2026-07-03-prep-module.md
-- 幂等：CREATE TABLE IF NOT EXISTS（可重复执行）
-- 约定：menu+ingredient 无记录即视为 PENDING（前端默认待备），不必预插
-- ============================================================

CREATE TABLE IF NOT EXISTS menu_prep_status (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  menu_id       BIGINT       NOT NULL                  COMMENT '食集 menu.id',
  ingredient_id BIGINT       NOT NULL                  COMMENT '食材 ingredient.id',
  status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待备/READY已备/THAWING化冻/MARINATING腌制',
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_menu_ingredient (menu_id, ingredient_id),
  KEY idx_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食集备菜备料状态';
