ALTER TABLE loan_requests
    ADD COLUMN appraisal_fee_rate DECIMAL(5,2) NULL AFTER proposed_interest_rate;
