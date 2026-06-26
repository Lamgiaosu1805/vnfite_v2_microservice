package com.p2plending.loan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoDebitSweepResponse {
    private String auditId;
    private String triggerSource;
    private String triggeredBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private int scannedLoans;
    private int dueLoans;
    private int settledFull;
    private int settledPartial;
    private int noBalance;
    private int balanceError;
    private int noDue;
    private int failed;
    private BigDecimal amountCollected;
    private String errorSummary;
}
