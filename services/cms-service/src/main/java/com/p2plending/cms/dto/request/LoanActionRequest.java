package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoanActionRequest {
    @Size(max = 500) private String reason;
}
