-- Fix 5: unique constraint on upload_id (partial, allows NULLs for CLI)
CREATE UNIQUE INDEX idx_analysis_upload_id_unique
    ON analysis_records(upload_id) WHERE upload_id IS NOT NULL;

-- Fix 6: optimistic locking version column
ALTER TABLE analysis_records ADD COLUMN version BIGINT DEFAULT 0;

-- Fix 19: foreign key
ALTER TABLE analysis_records ADD CONSTRAINT fk_analysis_upload
    FOREIGN KEY (upload_id) REFERENCES upload_records(id) ON DELETE SET NULL;

-- Fix 20: error_message to TEXT
ALTER TABLE analysis_records ALTER COLUMN error_message TYPE TEXT;
