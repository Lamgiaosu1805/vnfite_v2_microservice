package com.p2plending.auth.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeviceSessionResponse {
    private String deviceName;
    private String platform;
    private String loginAt;
    /** Luôn true với kiến trúc single-device — đây chính là thiết bị đang dùng */
    private boolean current;
}
