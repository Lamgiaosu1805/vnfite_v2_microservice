package com.p2plending.loan.dto.request;

import com.p2plending.loan.domain.enums.LoanStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoanStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private LoanStatus status;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
