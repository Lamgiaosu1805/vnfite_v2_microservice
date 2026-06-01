-- Migrate existing PENDING rows to PENDING_REVIEW before changing ENUM
UPDATE loan_requests SET status = 'PENDING_REVIEW' WHERE status = 'PENDING';

-- Add new personal-info columns (nullable — borrower fills at submission time)
ALTER TABLE loan_requests
  ADD COLUMN referred_by     VARCHAR(100)   NULL              AFTER purpose,
  ADD COLUMN monthly_income  DECIMAL(15,2)  NULL              AFTER referred_by,
  ADD COLUMN occupation      VARCHAR(100)   NULL              AFTER monthly_income,
  ADD COLUMN current_address VARCHAR(500)   NULL              AFTER occupation,
  ADD COLUMN rejection_reason VARCHAR(500)  NULL              AFTER funded_amount,
  ADD COLUMN reviewed_at     DATETIME       NULL              AFTER rejection_reason;

-- Make interest_rate nullable — CMS sets it during approval
ALTER TABLE loan_requests
  MODIFY COLUMN interest_rate DECIMAL(5,2) NULL;

-- Expand status ENUM: PENDING_REVIEW replaces PENDING, add REJECTED
ALTER TABLE loan_requests
  MODIFY COLUMN status ENUM(
    'PENDING_REVIEW',
    'ACTIVE',
    'FUNDED',
    'REPAYING',
    'COMPLETED',
    'DEFAULTED',
    'REJECTED'
  ) NOT NULL DEFAULT 'PENDING_REVIEW';
