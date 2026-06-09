-- Hợp đồng điện tử (mock VNPT eContract) cho luồng P2P.
--   INVESTMENT     : nhà đầu tư ký khi rót vốn (1 hợp đồng / offer).
--   LOAN_AGREEMENT : người gọi vốn ký khế ước nhận nợ khi khoản đã đủ vốn, trước khi giải ngân.
CREATE TABLE IF NOT EXISTS loan_contracts (
  id                   VARCHAR(36)   PRIMARY KEY,
  loan_id              VARCHAR(36)   NOT NULL,
  contract_type        ENUM('INVESTMENT','LOAN_AGREEMENT') NOT NULL,
  party_id             VARCHAR(36)   NOT NULL,
  offer_id             VARCHAR(36)   NULL,
  contract_no          VARCHAR(60)   NULL,
  amount               DECIMAL(15,2) NULL,
  interest_rate        DECIMAL(5,2)  NULL,
  term_months          INT           NULL,
  provider             VARCHAR(30)   NOT NULL DEFAULT 'MOCK_VNPT',
  provider_contract_id VARCHAR(100)  NULL,
  document_url         VARCHAR(500)  NULL,
  signed_document_url  VARCHAR(500)  NULL,
  status               ENUM('PENDING_SIGNATURE','SIGNED','VOIDED') NOT NULL DEFAULT 'PENDING_SIGNATURE',
  issued_at            DATETIME      NULL,
  signed_at            DATETIME      NULL,
  signed_via           VARCHAR(20)   NULL,
  is_deleted           TINYINT(1)    NOT NULL DEFAULT 0,
  created_at           DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_contract_loan   (loan_id),
  KEY idx_contract_party  (party_id),
  KEY idx_contract_offer  (offer_id),
  KEY idx_contract_status (status)
);
