-- Add auto-increment sequence for generating VNF loan codes
-- loan_code = "VNF" + LPAD(loan_seq, 6, '0') e.g. VNF000001
-- Must be a KEY for MySQL AUTO_INCREMENT to work on non-primary column
ALTER TABLE loan_requests
  ADD COLUMN loan_seq BIGINT UNIQUE KEY AUTO_INCREMENT;
