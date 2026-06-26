package com.p2plending.cms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetCustomerPasswordResponse {
    private String userId;
    private String phone;
    private String generatedPassword;
}
