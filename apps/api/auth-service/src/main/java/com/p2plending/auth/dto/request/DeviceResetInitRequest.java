package com.p2plending.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceResetInitRequest {

    @NotBlank(message = "Phone is required")
    private String phone;

    @NotBlank(message = "CCCD number is required")
    private String cccdNumber;

    /** Ngày cấp CCCD — định dạng yyyy-MM-dd */
    @NotBlank(message = "Issue date is required")
    private String issueDate;
}
