package com.p2plending.loan.kafka.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Phát khi OPS giải ngân vốn cho người gọi vốn (loan → DISBURSED). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanDisbursedEvent {
    private String loanId;
    private String loanCode;
    private String borrowerId;
    private BigDecimal amount;
    private LocalDateTime disbursedAt;
    /** userId của các nhà đầu tư đã rót vốn (offer ACCEPTED) — để gửi thông báo. */
    private List<String> investorIds;
}
