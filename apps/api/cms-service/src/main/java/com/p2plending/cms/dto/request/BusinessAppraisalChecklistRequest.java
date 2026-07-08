package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessAppraisalChecklistRequest {

    @NotBlank
    @Size(max = 80)
    private String category;

    @NotBlank
    @Size(max = 255)
    private String title;

    private String instruction;

    private boolean required;

    @NotBlank
    @Size(max = 30)
    private String status;

    private String note;

    private String evidenceRefs;
}
