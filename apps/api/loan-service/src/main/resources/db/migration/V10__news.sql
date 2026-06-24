CREATE TABLE IF NOT EXISTS news (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    title       VARCHAR(500) NOT NULL,
    subtitle    VARCHAR(1000),
    image_url   VARCHAR(500),
    content     LONGTEXT,
    news_type   VARCHAR(10),
    published_at DATETIME,
    is_deleted  TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_news_published_at ON news (published_at DESC);
