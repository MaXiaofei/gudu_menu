CREATE TABLE notification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  member_id BIGINT NOT NULL,
  type VARCHAR(32) NOT NULL,          -- expiry(临期)/shopping(采购)/prep(备菜)/...
  channel VARCHAR(32) NOT NULL,       -- in_app / wx_subscribe
  title VARCHAR(128),
  content VARCHAR(1024),
  is_read TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notif_member ON notification(member_id, is_read);
