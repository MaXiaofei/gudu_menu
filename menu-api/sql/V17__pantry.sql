-- V17: 食材库存模块（pantry）
-- 食材库存：记录家中现有食材的余量/单位/过期日/低库存阈值，支持临期与不足判定。
-- 食材/单位分别复用 ingredient、sys_dict(group=unit)，不冗余存储名称。
CREATE TABLE IF NOT EXISTS pantry (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  ingredient_id  BIGINT NOT NULL,                 -- 关联 ingredient.id
  amount         DECIMAL(10,2) NOT NULL DEFAULT 0, -- 当前余量
  unit_id        BIGINT,                          -- 关联 sys_dict(group=unit)
  expire_date    DATE,                            -- 过期日（可空，表示无明确保质期）
  low_threshold  DECIMAL(10,2) DEFAULT 0,         -- 低库存阈值（低于则提示采购）
  update_time    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        TINYINT DEFAULT 0
);

CREATE INDEX idx_pantry_ingredient ON pantry(ingredient_id);
CREATE INDEX idx_pantry_expire ON pantry(expire_date);
