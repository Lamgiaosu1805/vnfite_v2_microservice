package com.p2plending.loan.dto.response;

import com.p2plending.loan.domain.entity.LoanFeeConfig;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class LoanFeeConfigResponse {
    private Integer id;
    private String feeType;
    private String feeName;
    private BigDecimal feeAmount;
    private LoanFeeConfig.CalcType calcType;
    private BigDecimal vatRate;
    private boolean isActive;
    private String updatedBy;
    private LocalDateTime updatedAt;

    public static LoanFeeConfigResponse from(LoanFeeConfig cfg) {
        return LoanFeeConfigResponse.builder()
                .id(cfg.getId())
                .feeType(cfg.getFeeType())
                .feeName(cfg.getFeeName())
                .feeAmount(cfg.getFeeAmount())
                .calcType(cfg.getCalcType())
                .vatRate(cfg.getVatRate())
                .isActive(cfg.isActive())
                .updatedBy(cfg.getUpdatedBy())
                .updatedAt(cfg.getUpdatedAt())
                .build();
    }
}
