CREATE TABLE IF NOT EXISTS notifications (
  id         VARCHAR(36)  PRIMARY KEY,
  user_id    VARCHAR(36)  NOT NULL,
  title      VARCHAR(255) NOT NULL,
  message    TEXT         NOT NULL,
  type       ENUM('EMAIL','SMS','PUSH','IN_APP') NOT NULL,
  channel    VARCHAR(50),
  is_read    TINYINT(1)   NOT NULL DEFAULT 0,
  sent_at    DATETIME,
  is_deleted TINYINT(1)   NOT NULL DEFAULT 0,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_notif_user_id (user_id),
  KEY idx_notif_type    (type),
  KEY idx_notif_is_read (is_read)
);

CREATE TABLE IF NOT EXISTS notification_templates (
  id         VARCHAR(36)  PRIMARY KEY,
  code       VARCHAR(100) NOT NULL,
  title      VARCHAR(255),
  body       TEXT,
  type       VARCHAR(50),
  is_deleted TINYINT(1)   NOT NULL DEFAULT 0,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_template_code (code)
);
