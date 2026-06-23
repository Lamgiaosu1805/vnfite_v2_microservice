-- Mã giao dịch YFCH thật do TIKLUY/MB sinh, tách khỏi transfer_ref nội bộ.
-- Dùng để query trạng thái mà không retry mù khi callback bị trễ/mất.
ALTER TABLE withdrawal_requests
    ADD COLUMN provider_transfer_ref VARCHAR(100) NULL AFTER transfer_ref,
    ADD INDEX idx_wr_provider_transfer_ref (provider_transfer_ref);
