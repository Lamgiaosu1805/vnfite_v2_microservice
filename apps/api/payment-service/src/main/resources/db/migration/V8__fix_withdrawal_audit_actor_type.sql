-- Keep the audit actor type aligned with WithdrawalAuditLog.actorType (String).
-- V5 used a native MySQL ENUM, which Hibernate validates as CHAR instead of VARCHAR.
ALTER TABLE withdrawal_audit_logs
    MODIFY COLUMN actor_type VARCHAR(10) NOT NULL;
