CREATE TABLE IF NOT EXISTS job_posting (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    title         VARCHAR(500) NOT NULL,
    position      VARCHAR(255),
    salary        VARCHAR(255),
    locations     VARCHAR(255),
    industry_type VARCHAR(50),
    working_form  VARCHAR(100),
    experience    VARCHAR(255),
    work_model    VARCHAR(100),
    degree        VARCHAR(255),
    description   LONGTEXT,
    image_url     VARCHAR(500),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    published_at  DATETIME,
    is_deleted    TINYINT(1)   NOT NULL DEFAULT 0,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_job_posting_published_at ON job_posting (published_at DESC);

CREATE TABLE IF NOT EXISTS job_application (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    job_posting_id VARCHAR(36)  NOT NULL,
    full_name      VARCHAR(255) NOT NULL,
    phone_number   VARCHAR(20)  NOT NULL,
    email          VARCHAR(255),
    location       VARCHAR(255),
    introduction   TEXT,
    cv_file_path   VARCHAR(500) NOT NULL,
    cv_file_name   VARCHAR(255),
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_job_application_posting FOREIGN KEY (job_posting_id) REFERENCES job_posting(id)
);

CREATE INDEX idx_job_application_posting ON job_application (job_posting_id, created_at DESC);
