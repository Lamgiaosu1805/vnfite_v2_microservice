package com.p2plending.auth.dto.request;

import com.p2plending.auth.validation.VietnamesePhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OtpIpUnblockRequestPayload {
    @NotBlank
    @VietnamesePhone
    private String phone;

    @Size(max = 255)
    private String note;
}
