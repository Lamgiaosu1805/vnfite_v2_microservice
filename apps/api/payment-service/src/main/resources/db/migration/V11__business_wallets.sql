-- Giai đoạn B: một user có ví cá nhân và ví doanh nghiệp riêng.
-- Backward compatible: dữ liệu cũ là PERSONAL; API cũ không truyền ownerType vẫn dùng PERSONAL.

DROP PROCEDURE IF EXISTS add_payment_column_if_missing;

DELIMITER //
CREATE PROCEDURE add_payment_column_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table_name, ' ADD COLUMN ', p_column_name, ' ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL add_payment_column_if_missing('wallets', 'owner_type',
    'VARCHAR(20) NOT NULL DEFAULT ''PERSONAL'' AFTER user_id');
CALL add_payment_column_if_missing('linked_banks', 'owner_type',
    'VARCHAR(20) NOT NULL DEFAULT ''PERSONAL'' AFTER user_id');
CALL add_payment_column_if_missing('withdrawal_requests', 'owner_type',
    'VARCHAR(20) NOT NULL DEFAULT ''PERSONAL'' AFTER user_id');

DROP PROCEDURE add_payment_column_if_missing;

UPDATE wallets SET owner_type = 'PERSONAL' WHERE owner_type IS NULL OR owner_type = '';
UPDATE linked_banks SET owner_type = 'PERSONAL' WHERE owner_type IS NULL OR owner_type = '';
UPDATE withdrawal_requests SET owner_type = 'PERSONAL' WHERE owner_type IS NULL OR owner_type = '';

DROP PROCEDURE IF EXISTS drop_wallet_user_unique_if_needed;

DELIMITER //
CREATE PROCEDURE drop_wallet_user_unique_if_needed()
BEGIN
    SET @wallet_user_unique = NULL;

    SELECT s.index_name
    INTO @wallet_user_unique
    FROM information_schema.statistics s
    JOIN (
        SELECT index_name, COUNT(*) AS col_count
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'wallets'
          AND non_unique = 0
        GROUP BY index_name
    ) c ON c.index_name = s.index_name
    WHERE s.table_schema = DATABASE()
      AND s.table_name = 'wallets'
      AND s.non_unique = 0
      AND s.column_name = 'user_id'
      AND c.col_count = 1
      AND s.index_name <> 'PRIMARY'
    LIMIT 1;

    IF @wallet_user_unique IS NOT NULL THEN
        SET @ddl = CONCAT('ALTER TABLE wallets DROP INDEX ', @wallet_user_unique);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL drop_wallet_user_unique_if_needed();

DROP PROCEDURE drop_wallet_user_unique_if_needed;

DROP PROCEDURE IF EXISTS add_payment_index_if_missing;

DELIMITER //
CREATE PROCEDURE add_payment_index_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table_name, ' ADD ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL add_payment_index_if_missing('wallets', 'uk_wallet_user_owner',
    'UNIQUE KEY uk_wallet_user_owner (user_id, owner_type)');
CALL add_payment_index_if_missing('wallets', 'idx_wallet_user_owner',
    'INDEX idx_wallet_user_owner (user_id, owner_type)');
CALL add_payment_index_if_missing('linked_banks', 'idx_linked_banks_user_owner',
    'INDEX idx_linked_banks_user_owner (user_id, owner_type)');
CALL add_payment_index_if_missing('withdrawal_requests', 'idx_withdrawal_user_owner',
    'INDEX idx_withdrawal_user_owner (user_id, owner_type)');

DROP PROCEDURE add_payment_index_if_missing;
