-- V41: 库存批次增加「存放方式」（手动添加入库时选：常温/冷藏/冷冻，对齐 pantry-manual-add.html 批次属性）
ALTER TABLE pantry ADD COLUMN storage VARCHAR(16) NULL
  COMMENT '存放方式：常温/冷藏/冷冻（手动添加批次属性，可空）' AFTER expire_date;
