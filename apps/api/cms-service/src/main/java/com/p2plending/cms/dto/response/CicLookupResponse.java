package com.p2plending.cms.dto.response;

import com.p2plending.cms.domain.entity.CicManualLookup;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Kết quả tra cứu CIC nhập tay đang lưu cho một khoản. */
@Data
@Builder
public class CicLookupResponse {

    private String id;
    private String loanId;
    private Integer debtGroup;
    private Integer maxDpd;
    private Integer activeLenders;
    private BigDecimal totalOutstanding;
    private Integer inquiriesRecent;
    private LocalDate checkedAt;
    private String attachmentFileId;
    private String note;
    private boolean consentConfirmed;
    private String enteredBy;
    private LocalDateTime createdAt;

    public static CicLookupResponse from(CicManualLookup e) {
        if (e == null) return null;
        return CicLookupResponse.builder()
                .id(e.getId())
                .loanId(e.getLoanId())
                .debtGroup(e.getDebtGroup())
                .maxDpd(e.getMaxDpd())
                .activeLenders(e.getActiveLenders())
                .totalOutstanding(e.getTotalOutstanding())
                .inquiriesRecent(e.getInquiriesRecent())
                .checkedAt(e.getCheckedAt())
                .attachmentFileId(e.getAttachmentFileId())
                .note(e.getNote())
                .consentConfirmed(e.isConsentConfirmed())
                .enteredBy(e.getEnteredBy())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
