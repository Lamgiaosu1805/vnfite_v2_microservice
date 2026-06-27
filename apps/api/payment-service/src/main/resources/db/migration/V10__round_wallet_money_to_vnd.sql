-- VNFITE uses integer VND amounts for wallet ledger money.
-- Data-preserving cleanup for wallet data migrated/generated before the integer-money rule.
UPDATE wallets
SET locked_balance = ROUND(locked_balance, 0)
WHERE is_deleted = 0;

UPDATE wallet_transactions
SET amount = ROUND(amount, 0),
    balance_after = CASE
        WHEN balance_after IS NULL THEN NULL
        ELSE ROUND(balance_after, 0)
    END
WHERE is_deleted = 0;
