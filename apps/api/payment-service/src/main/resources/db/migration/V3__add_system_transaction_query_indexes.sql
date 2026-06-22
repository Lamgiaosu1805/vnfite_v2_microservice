CREATE INDEX idx_wallet_transactions_type_status_created
    ON wallet_transactions (type, status, created_at);

CREATE INDEX idx_wallet_transactions_created_at
    ON wallet_transactions (created_at);
