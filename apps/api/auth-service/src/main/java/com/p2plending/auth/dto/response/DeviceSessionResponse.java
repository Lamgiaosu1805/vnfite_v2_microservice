package com.p2plending.auth.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeviceSessionResponse {
    private String        deviceName;
    private String        platform;
    /** Thời điểm đăng nhập gần nhất của thiết bị này */
    private LocalDateTime loginAt;
    /** true = thiết bị này đang giữ phiên đăng nhập hiện tại */
    private boolean       current;
}
