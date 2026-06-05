package com.p2plending.auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dữ liệu phiên thiết bị lưu trong Redis key device_session:{phone}.
 * Không có TTL — tồn tại cho đến khi user đăng xuất hoặc device reset.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSessionData {
    private String deviceKey;
    private String deviceName;
    private String platform;
    private String loginAt;
}
