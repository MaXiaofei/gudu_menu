-- 账号表 + 管理员种子（admin / admin123）
CREATE TABLE IF NOT EXISTS user (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  username    VARCHAR(64)  NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  nickname    VARCHAR(64),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted     TINYINT  DEFAULT 0
);

INSERT INTO user(username, password_hash, nickname)
VALUES ('admin', '$2a$10$UM6Ql8.rTKA1aGBSup2ACu4ol4WikGIlfyAPPSciymgdPMpWqa/Ba', '掌勺人');
