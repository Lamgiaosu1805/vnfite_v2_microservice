package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserBlacklistRequest {
    private boolean blacklisted;

    @Size(max = 255)
    private String reason;
}
