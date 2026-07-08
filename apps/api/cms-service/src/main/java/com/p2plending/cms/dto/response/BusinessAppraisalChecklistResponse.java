package com.p2plending.cms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BusinessAppraisalChecklistResponse {
    private String id;
    private String loanId;
    private String checklistCode;
    private String category;
    private String title;
    private String instruction;
    private boolean required;
    private String status;
    private String note;
    private String evidenceRefs;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
