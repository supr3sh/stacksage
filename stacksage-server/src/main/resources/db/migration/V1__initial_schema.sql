CREATE TABLE upload_records (
    id                  VARCHAR(36)     PRIMARY KEY,
    original_filename   VARCHAR(255)    NOT NULL,
    stored_filename     VARCHAR(255)    NOT NULL UNIQUE,
    file_size           BIGINT          NOT NULL DEFAULT 0,
    status              VARCHAR(20)     NOT NULL DEFAULT 'UPLOADED',
    created_at          TIMESTAMP       NOT NULL
);

CREATE TABLE analysis_records (
    id              VARCHAR(36)     PRIMARY KEY,
    upload_id       VARCHAR(36),
    source          VARCHAR(255),
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    results_json    TEXT,
    error_message   VARCHAR(255),
    created_at      TIMESTAMP       NOT NULL,
    completed_at    TIMESTAMP
);

CREATE INDEX idx_analysis_upload_id ON analysis_records(upload_id);
