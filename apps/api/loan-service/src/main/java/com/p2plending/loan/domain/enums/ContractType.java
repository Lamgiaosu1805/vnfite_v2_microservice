package com.p2plending.loan.domain.enums;

/** Loại hợp đồng điện tử trong luồng P2P. */
public enum ContractType {
    /** Hợp đồng đầu tư — nhà đầu tư ký khi rót vốn vào một khoản gọi vốn. */
    INVESTMENT,
    /** Hợp đồng vay (khế ước nhận nợ) — người gọi vốn ký khi khoản đã đủ vốn, trước khi giải ngân. */
    LOAN_AGREEMENT
}
