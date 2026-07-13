ALTER TABLE fee_revenue_ledger
    ADD COLUMN is_reversed TINYINT(1) NOT NULL DEFAULT 0 AFTER is_deleted;
