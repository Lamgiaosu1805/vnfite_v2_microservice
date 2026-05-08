package com.p2plending.auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingRegistration {
    private String phone;
    private String hashedPassword;
    private String referrerPhone;
    private String otp;
}
