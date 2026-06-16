-- Optimistic locking: ngăn hai luồng song song ghi đè cùng một kỳ trả nợ.
-- Hibernate dùng cột này để phát hiện conflict và ném OptimisticLockingFailureException.
ALTER TABLE repayment_schedule
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
