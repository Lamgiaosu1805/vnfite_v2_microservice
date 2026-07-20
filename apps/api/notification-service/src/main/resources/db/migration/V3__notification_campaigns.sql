CREATE TABLE notification_campaigns (
    id                  VARCHAR(36)  NOT NULL PRIMARY KEY,
    title               VARCHAR(255) NOT NULL,
    body                VARCHAR(1000) NOT NULL,
    campaign_type       VARCHAR(20)  NOT NULL,
    segment_kyc_status  VARCHAR(20)  NULL,
    send_mode           VARCHAR(20)  NOT NULL,
    scheduled_time       TIME         NULL,
    start_date           DATE         NULL,
    end_date             DATE         NULL,
    status               VARCHAR(20)  NOT NULL,
    last_sent_date        DATE         NULL,
    total_sent_count      INT          NOT NULL DEFAULT 0,
    created_by            VARCHAR(36)  NULL,
    is_deleted            TINYINT(1)   NOT NULL DEFAULT 0,
    created_at             DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_notif_campaign_status ON notification_campaigns (status, send_mode);
