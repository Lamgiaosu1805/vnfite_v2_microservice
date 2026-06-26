package com.p2plending.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalCustomerPasswordResetResponse {
    private String userId;
    private String phone;
    private String generatedPassword;
}
