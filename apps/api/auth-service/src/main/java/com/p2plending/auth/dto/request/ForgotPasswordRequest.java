package com.p2plending.auth.dto.request;

import com.p2plending.auth.validation.VietnameseCitizenId;
import com.p2plending.auth.validation.VietnamesePhone;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "Phone number is required")
    @VietnamesePhone
    private String phone;

    @VietnameseCitizenId(message = "CCCD must be 12 digits")
    private String cccdNumber;
}
