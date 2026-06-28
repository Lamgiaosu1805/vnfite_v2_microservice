ALTER TABLE loan_requests
    ADD COLUMN workplace_address VARCHAR(500) DEFAULT NULL AFTER workplace;
