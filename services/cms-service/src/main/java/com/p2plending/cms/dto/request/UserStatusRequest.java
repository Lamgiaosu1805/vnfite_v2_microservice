package com.p2plending.cms.dto.request;

import com.p2plending.cms.domain.enums.UserAccountStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserStatusRequest {
    @NotNull private UserAccountStatus status;
    @Size(max = 500) private String reason;
}
