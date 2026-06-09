package com.p2plending.loan.kafka.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Phát khi hợp đồng vay của người gọi vốn đã sẵn sàng để ký (khoản vừa FUNDED). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContractReadyEvent {
    private String loanId;
    private String loanCode;
    private String borrowerId;
    private String contractId;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private Integer termMonths;
    private LocalDateTime issuedAt;
}
