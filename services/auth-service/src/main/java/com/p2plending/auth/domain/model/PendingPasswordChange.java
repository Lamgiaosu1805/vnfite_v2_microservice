package com.p2plending.auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingPasswordChange {
    private String newPasswordHash;
    private String otp;
}
