-- Step 1: Expand ENUM to include PENDING_REVIEW and REJECTED
--   (keep PENDING temporarily so existing rows are still valid)
ALTER TABLE loan_requests
  MODIFY COLUMN status ENUM(
    'PENDING', 'PENDING_REVIEW',
    'ACTIVE', 'FUNDED', 'REPAYING', 'COMPLETED', 'DEFAULTED', 'REJECTED'
  ) NOT NULL DEFAULT 'PENDING';

-- Step 2: Migrate existing PENDING rows → PENDING_REVIEW
UPDATE loan_requests SET status = 'PENDING_REVIEW' WHERE status = 'PENDING';

-- Step 3: Remove PENDING from ENUM and set new default
ALTER TABLE loan_requests
  MODIFY COLUMN status ENUM(
    'PENDING_REVIEW', 'ACTIVE', 'FUNDED', 'REPAYING', 'COMPLETED', 'DEFAULTED', 'REJECTED'
  ) NOT NULL DEFAULT 'PENDING_REVIEW';

-- Step 4: Make interest_rate nullable — CMS sets it during approval
ALTER TABLE loan_requests
  MODIFY COLUMN interest_rate DECIMAL(5,2) NULL;

-- Step 5: Add personal-info and review columns
ALTER TABLE loan_requests
  ADD COLUMN referred_by      VARCHAR(100)   NULL AFTER purpose,
  ADD COLUMN monthly_income   DECIMAL(15,2)  NULL AFTER referred_by,
  ADD COLUMN occupation       VARCHAR(100)   NULL AFTER monthly_income,
  ADD COLUMN current_address  VARCHAR(500)   NULL AFTER occupation,
  ADD COLUMN rejection_reason VARCHAR(500)   NULL AFTER funded_amount,
  ADD COLUMN reviewed_at      DATETIME       NULL AFTER rejection_reason;
