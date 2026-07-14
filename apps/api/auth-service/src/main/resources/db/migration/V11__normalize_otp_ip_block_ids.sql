-- V10 was first deployed with CHAR(36), while Hibernate maps String identifiers as VARCHAR(36).
-- Normalize both tables so schema validation is stable in every environment.
ALTER TABLE otp_ip_blocks MODIFY id VARCHAR(36) NOT NULL;
ALTER TABLE otp_ip_unblock_requests MODIFY id VARCHAR(36) NOT NULL;
