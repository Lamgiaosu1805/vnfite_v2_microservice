package com.p2plending.loan.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Phát khi job đối soát cộng bù thành công tiền hoàn trả cho một nhà đầu tư (lần trả nợ trước bị lỗi
 * cộng ví). notification-service thông báo cho nhà đầu tư đó. Tách riêng để không gửi lại cho người
 * gọi vốn (khác với {@link LoanRepaidEvent}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentCreditReconciledEvent {
    private String loanId;
    private String loanCode;
    private String investorId;
    private BigDecimal amount;
}
