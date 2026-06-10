package com.p2plending.payment.dto.response;

import com.p2plending.payment.domain.enums.TransactionStatus;
import com.p2plending.payment.domain.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionResponse {
    private String id;
    private TransactionType type;
    private BigDecimal amount;
    private TransactionStatus status;
    private String description;
    private BigDecimal balanceAfter;
    private LocalDateTime createdAt;
}
