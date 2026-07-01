package com.p2plending.auth.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BlacklistInternalRequest {
    private boolean blacklisted;

    @Size(max = 255)
    private String reason;
}
