package com.p2plending.notification.kafka.event;

import lombok.Data;

import java.math.BigDecimal;

/** Job đối soát đã cộng bù tiền hoàn trả cho một nhà đầu tư (lần trả nợ trước bị lỗi cộng ví). */
@Data
public class InvestmentCreditReconciledEvent {
    private String loanId;
    private String loanCode;
    private String investorId;
    private BigDecimal amount;
}
