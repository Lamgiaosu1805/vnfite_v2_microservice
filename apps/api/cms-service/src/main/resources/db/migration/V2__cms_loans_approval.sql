-- Make interest_rate nullable — CMS sets it only after approval
ALTER TABLE cms_loans
  MODIFY COLUMN interest_rate DECIMAL(5,2) NULL;

-- Add loan code (VNF000001 format) synced from loan.submitted event
ALTER TABLE cms_loans
  ADD COLUMN loan_code VARCHAR(20) NULL AFTER loan_id;

-- Add borrower personal-info columns (synced from loan.submitted event)
ALTER TABLE cms_loans
  ADD COLUMN occupation       VARCHAR(100)   NULL              AFTER purpose,
  ADD COLUMN monthly_income   DECIMAL(15,2)  NULL              AFTER occupation,
  ADD COLUMN current_address  VARCHAR(500)   NULL              AFTER monthly_income,
  ADD COLUMN referred_by      VARCHAR(100)   NULL              AFTER current_address,
  ADD COLUMN rejection_reason VARCHAR(500)   NULL              AFTER referred_by,
  ADD COLUMN reviewed_by      VARCHAR(100)   NULL              AFTER rejection_reason,
  ADD COLUMN reviewed_at      DATETIME       NULL              AFTER reviewed_by;

-- Rename PENDING → PENDING_REVIEW in existing rows
UPDATE cms_loans SET status = 'PENDING_REVIEW' WHERE status = 'PENDING';
