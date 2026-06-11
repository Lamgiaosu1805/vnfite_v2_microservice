-- loan-service uses UUID string IDs. Keep credit-service references aligned.
ALTER TABLE credit_scores
    MODIFY COLUMN loan_request_id VARCHAR(36) NULL COMMENT 'FK -> loan_db.loan_requests.id (UUID, nullable for pre-score)';

ALTER TABLE feature_snapshots
    MODIFY COLUMN loan_request_id VARCHAR(36) NULL;

ALTER TABLE document_analyses
    MODIFY COLUMN loan_request_id VARCHAR(36) NULL;
