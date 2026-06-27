-- VNFITE uses integer VND amounts for repayment/ledger money.
-- Data-preserving cleanup for schedules/transactions generated before the integer-money rule.
UPDATE repayment_schedule
SET principal_due = ROUND(principal_due, 0),
    interest_due = ROUND(interest_due, 0),
    total_due = ROUND(total_due, 0),
    paid_amount = ROUND(paid_amount, 0),
    late_fee = ROUND(late_fee, 0),
    late_fee_paid = ROUND(late_fee_paid, 0)
WHERE is_deleted = 0;

UPDATE repayment_transaction
SET amount = ROUND(amount, 0)
WHERE is_deleted = 0;

