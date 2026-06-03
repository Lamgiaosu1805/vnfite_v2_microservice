package com.p2plending.loan.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoanCancelRequest {

    @Size(max = 500, message = "Lý do hủy không được quá 500 ký tự")
    private String reason;
}
