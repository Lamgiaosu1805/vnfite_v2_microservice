ALTER TABLE loan_requests
    ADD COLUMN workplace VARCHAR(255) DEFAULT NULL AFTER occupation;
