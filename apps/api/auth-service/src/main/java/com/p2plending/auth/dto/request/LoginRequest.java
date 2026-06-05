package com.p2plending.auth.dto.request;

import com.p2plending.auth.validation.VietnamesePhone;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Phone number is required")
    @VietnamesePhone
    private String phone;

    @NotBlank(message = "Password is required")
    private String password;

    /** UUID thiết bị — dùng để nhận diện "thiết bị này đã đăng nhập trước đó" */
    private String deviceKey;

    /** Tên thiết bị hiển thị cho user (vd: "iPhone (iOS 17.2)") */
    private String deviceName;

    /** Platform: "ios" | "android" */
    private String platform;
}
