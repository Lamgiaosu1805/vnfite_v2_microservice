package com.p2plending.auth.controller;

import com.p2plending.auth.dto.request.ChangePasswordInitRequest;
import com.p2plending.auth.dto.request.ChangePasswordVerifyRequest;
import com.p2plending.auth.dto.request.CheckPhoneRequest;
import com.p2plending.auth.dto.request.ForgotPasswordCheckRequest;
import com.p2plending.auth.dto.request.ForgotPasswordOtpVerifyRequest;
import com.p2plending.auth.dto.request.ForgotPasswordRequest;
import com.p2plending.auth.dto.request.ForgotPasswordResetRequest;
import com.p2plending.auth.dto.request.BusinessProfileSubmitRequest;
import com.p2plending.auth.dto.request.KycInitRequest;
import com.p2plending.auth.dto.request.KycVerifyRequest;
import com.p2plending.auth.dto.request.LoginRequest;
import com.p2plending.auth.dto.request.OtpVerifyRequest;
import com.p2plending.auth.dto.request.OtpIpUnblockRequestPayload;
import com.p2plending.auth.dto.request.BiometricChallengeRequest;
import com.p2plending.auth.dto.request.BiometricEnableRequest;
import com.p2plending.auth.dto.request.BiometricLoginRequest;
import com.p2plending.auth.dto.request.DeviceResetInitRequest;
import com.p2plending.auth.dto.request.DeviceResetVerifyRequest;
import com.p2plending.auth.dto.request.RefreshTokenRequest;
import com.p2plending.auth.dto.request.RegisterRequest;
import com.p2plending.auth.dto.response.AuthResponse;
import com.p2plending.auth.dto.response.BusinessProfileResponse;
import com.p2plending.auth.dto.response.DeviceSessionResponse;
import com.p2plending.auth.dto.response.KycSubmissionResponse;
import com.p2plending.auth.dto.response.RegisterInitResponse;
import com.p2plending.auth.dto.response.UserProfileResponse;
import com.p2plending.auth.dto.response.VnptEkycTokenResponse;
import com.p2plending.auth.service.AuthService;
import com.p2plending.auth.service.BusinessProfileService;
import com.p2plending.auth.service.ChangePasswordService;
import com.p2plending.auth.service.FcmTokenService;
import com.p2plending.auth.service.KycService;
import com.p2plending.auth.service.PasswordResetService;
import com.p2plending.auth.service.OtpIpBlockService;
import com.p2plending.auth.service.VnptEkycTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService            authService;
    private final KycService             kycService;
    private final PasswordResetService   passwordResetService;
    private final ChangePasswordService  changePasswordService;
    private final FcmTokenService        fcmTokenService;
    private final VnptEkycTokenService   vnptEkycTokenService;
    private final BusinessProfileService businessProfileService;
    private final OtpIpBlockService       otpIpBlockService;

    /**
     * POST /api/auth/check-phone
     * Bước 1: Kiểm tra số điện thoại đã tồn tại chưa.
     * Response: { "available": true/false }
     */
    @PostMapping("/check-phone")
    public ResponseEntity<Map<String, Boolean>> checkPhone(@Valid @RequestBody CheckPhoneRequest request) {
        return ResponseEntity.ok(Map.of("available", authService.isPhoneAvailable(request.getPhone())));
    }

    /**
     * POST /api/auth/register
     * Bước 2: Validate thông tin, gửi OTP.
     * Môi trường test: OTP luôn là "000000" và được trả về trong response.
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterInitResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = resolveClientIp(servletRequest);
        otpIpBlockService.assertRegistrationAllowed(clientIp);
        return ResponseEntity.ok(authService.registerInit(request, clientIp));
    }

    /** Khách bị chặn OTP vẫn có thể gửi yêu cầu CSKH; IP được server tự xác định. */
    @PostMapping("/register/unblock-request")
    public ResponseEntity<Map<String, String>> requestRegisterUnblock(
            @Valid @RequestBody OtpIpUnblockRequestPayload request,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(otpIpBlockService.createUnblockRequest(
                request.getPhone(), request.getNote(), resolveClientIp(servletRequest)));
    }

    /**
     * POST /api/auth/register/verify
     * Bước 3: Xác thực OTP → tạo tài khoản → trả về JWT.
     */
    @PostMapping("/register/verify")
    public ResponseEntity<AuthResponse> registerVerify(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerVerify(request));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    /**
     * GET /api/auth/devices
     * Danh sách các thiết bị đã từng đăng nhập tài khoản này.
     * Mỗi thiết bị (deviceKey) chỉ xuất hiện 1 lần với lần login gần nhất.
     * Thiết bị đang giữ phiên active được đánh dấu current=true.
     */
    @GetMapping("/devices")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DeviceSessionResponse>> getDevices(
            @AuthenticationPrincipal UserDetails userDetails) {
        String phone = userDetails.getUsername();
        String userId = authService.getUserIdByPhone(phone);
        List<DeviceSessionResponse> result = authService.getDeviceHistory(userId, phone);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/auth/logout
     * Đăng xuất server-side: xóa device_session và refresh_token khỏi Redis.
     * Yêu cầu JWT hợp lệ.
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails userDetails) {
        authService.serverLogout(userDetails.getUsername()); // username = phone
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/auth/biometric/challenge
     * Bước 1 đăng nhập sinh trắc học: cấp challenge (nonce) để thiết bị ký. Không cần JWT.
     */
    @PostMapping("/biometric/challenge")
    public ResponseEntity<Map<String, String>> biometricChallenge(
            @Valid @RequestBody BiometricChallengeRequest request) {
        return ResponseEntity.ok(authService.createBiometricChallenge(request.getPhone()));
    }

    /**
     * POST /api/auth/biometric/login
     * Bước 2: thiết bị gửi chữ ký của challenge. Server verify bằng public key đã lưu.
     * Không có shared secret truyền qua mạng — không cần JWT, không giới hạn thời gian.
     */
    @PostMapping("/biometric/login")
    public ResponseEntity<AuthResponse> biometricLogin(@Valid @RequestBody BiometricLoginRequest request) {
        return ResponseEntity.ok(authService.biometricLogin(
                request.getPhone(), request.getSignature(),
                request.getDeviceKey(), request.getDeviceName(), request.getPlatform()));
    }

    /**
     * POST /api/auth/device-reset/init
     * Xác minh danh tính qua CCCD để đặt lại session (dùng khi mất thiết bị cũ).
     */
    @PostMapping("/device-reset/init")
    public ResponseEntity<Map<String, String>> deviceResetInit(
            @Valid @RequestBody DeviceResetInitRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = resolveClientIp(servletRequest);
        otpIpBlockService.assertRegistrationAllowed(clientIp);
        return ResponseEntity.ok(authService.initDeviceReset(
                request.getPhone(), request.getCccdNumber(), request.getIssueDate(), clientIp));
    }

    /**
     * POST /api/auth/device-reset/verify
     * Xác nhận OTP để vô hiệu hoá toàn bộ session cũ.
     */
    @PostMapping("/device-reset/verify")
    public ResponseEntity<Map<String, String>> deviceResetVerify(@Valid @RequestBody DeviceResetVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyDeviceReset(request.getPhone(), request.getOtp()));
    }

    /**
     * POST /api/auth/biometric/init
     * Gửi OTP để xác nhận bật đăng nhập sinh trắc học trên app.
     */
    @PostMapping("/biometric/init")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> biometricInit(
            @AuthenticationPrincipal UserDetails userDetails) {
        String phone = userDetails.getUsername();
        String userId = authService.getUserIdByPhone(phone);
        return ResponseEntity.ok(authService.initBiometric(userId, phone));
    }

    /**
     * POST /api/auth/biometric/verify
     * Xác thực OTP + lưu public key của thiết bị để bật đăng nhập sinh trắc học.
     */
    @PostMapping("/biometric/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> biometricVerify(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BiometricEnableRequest request) {
        String phone = userDetails.getUsername();
        String userId = authService.getUserIdByPhone(phone);
        return ResponseEntity.ok(authService.verifyBiometricEnable(userId, request));
    }

    /**
     * POST /api/auth/biometric/disable/init
     * Gửi OTP để xác nhận tắt đăng nhập sinh trắc học trên app.
     */
    @PostMapping("/biometric/disable/init")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> biometricDisableInit(
            @AuthenticationPrincipal UserDetails userDetails) {
        String phone = userDetails.getUsername();
        String userId = authService.getUserIdByPhone(phone);
        return ResponseEntity.ok(authService.initBiometricDisable(userId, phone));
    }

    /**
     * POST /api/auth/biometric/disable/verify
     * Xác thực OTP trước khi app tắt trạng thái sinh trắc học cục bộ.
     */
    @PostMapping("/biometric/disable/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> biometricDisableVerify(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody KycVerifyRequest request) {
        String phone = userDetails.getUsername();
        String userId = authService.getUserIdByPhone(phone);
        return ResponseEntity.ok(authService.verifyBiometricDisable(userId, request));
    }

    /**
     * POST /api/auth/forgot-password/check
     * Bước 0: Kiểm tra phone → trả về { "requiresCccd": boolean }.
     * Frontend dùng để quyết định hiện/ẩn ô nhập CCCD trước khi gửi OTP.
     */
    @PostMapping("/forgot-password/check")
    public ResponseEntity<Map<String, Object>> forgotPasswordCheck(
            @Valid @RequestBody ForgotPasswordCheckRequest request) {
        return ResponseEntity.ok(passwordResetService.checkPhone(request));
    }

    /**
     * POST /api/auth/forgot-password
     * Bước 1: Xác minh danh tính, gửi OTP đặt lại mật khẩu.
     * - Chưa eKYC: chỉ cần phone.
     * - Đã eKYC: phone + cccdNumber.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = resolveClientIp(servletRequest);
        otpIpBlockService.assertRegistrationAllowed(clientIp);
        return ResponseEntity.ok(passwordResetService.initReset(request, clientIp));
    }

    /**
     * POST /api/auth/forgot-password/verify-otp
     * Bước 2: Xác thực OTP → trả về resetToken (TTL 10 phút).
     * Frontend dùng resetToken này để gọi bước tiếp theo.
     */
    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<Map<String, String>> forgotPasswordVerifyOtp(
            @Valid @RequestBody ForgotPasswordOtpVerifyRequest request) {
        return ResponseEntity.ok(passwordResetService.verifyOtp(request));
    }

    /**
     * POST /api/auth/forgot-password/reset
     * Bước 3: Dùng resetToken + mật khẩu mới → hoàn tất đặt lại mật khẩu.
     */
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<Map<String, String>> forgotPasswordReset(
            @Valid @RequestBody ForgotPasswordResetRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập lại."));
    }

    /**
     * POST /api/auth/change-password/init
     * Bước 1: Xác minh mật khẩu hiện tại → gửi OTP xác nhận đổi mật khẩu.
     */
    @PostMapping("/change-password/init")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> changePasswordInit(
            @Valid @RequestBody ChangePasswordInitRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        String userId = authService.getUserIdByPhone(principal.getUsername());
        return ResponseEntity.ok(changePasswordService.initChange(userId, principal.getUsername(), request));
    }

    /**
     * POST /api/auth/change-password/verify
     * Bước 2: Xác thực OTP → đặt mật khẩu mới, vô hiệu hoá phiên cũ.
     */
    @PostMapping("/change-password/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> changePasswordVerify(
            @Valid @RequestBody ChangePasswordVerifyRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        String userId = authService.getUserIdByPhone(principal.getUsername());
        changePasswordService.verifyChange(userId, principal.getUsername(), request);
        return ResponseEntity.ok(Map.of("message", "Mật khẩu đã được thay đổi thành công. Vui lòng đăng nhập lại."));
    }

    /**
     * GET /api/auth/ekyc/vnpt-token
     * Lấy phiên định danh VNPT (accessToken/tokenId/tokenKey) cho mobile khởi tạo SDK eKYC.
     * username/password VNPT giữ ở backend, không lộ ra app.
     */
    @GetMapping("/ekyc/vnpt-token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<VnptEkycTokenResponse> vnptEkycToken() {
        return ResponseEntity.ok(vnptEkycTokenService.getToken());
    }

    /**
     * POST /api/auth/kyc/init
     * Bước 1: Kiểm tra CCCD, upload ảnh, gửi OTP.
     */
    @PostMapping(value = "/kyc/init", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> kycInit(
            @Valid @ModelAttribute KycInitRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        String phone = principal.getUsername();
        String userId = authService.getUserIdByPhone(phone);
        return ResponseEntity.ok(kycService.initKyc(userId, phone, request));
    }

    /**
     * POST /api/auth/kyc/verify
     * Bước 2: Xác thực OTP → lưu thông tin KYC.
     */
    @PostMapping("/kyc/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<KycSubmissionResponse> kycVerify(
            @Valid @RequestBody KycVerifyRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        String userId = authService.getUserIdByPhone(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(kycService.verifyKyc(userId, request));
    }

    /**
     * POST /api/auth/devices/fcm-token
     * Mobile gọi sau khi nhận FCM token từ Firebase SDK (hoặc khi token refresh).
     * Body: { "fcmToken": "...", "deviceKey": "..." }
     */
    @PostMapping("/devices/fcm-token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> saveFcmToken(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails principal
    ) {
        String userId = authService.getUserIdByPhone(principal.getUsername());
        String fcmToken = body.get("fcmToken");
        String deviceKey = body.get("deviceKey");
        if (fcmToken == null || fcmToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "fcmToken là bắt buộc"));
        }
        fcmTokenService.saveToken(userId, fcmToken, deviceKey);
        return ResponseEntity.ok(Map.of("message", "FCM token đã được lưu"));
    }

    /**
     * POST /api/auth/business-profile
     * Nộp hồ sơ doanh nghiệp (multipart: thông tin DN + 1–3 ảnh GPKD).
     * Yêu cầu eKYC cá nhân APPROVED. Hồ sơ chờ admin CMS duyệt tay.
     */
    @PostMapping(value = "/business-profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BusinessProfileResponse> submitBusinessProfile(
            @Valid @ModelAttribute BusinessProfileSubmitRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        String userId = authService.getUserIdByPhone(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(businessProfileService.submit(userId, request));
    }

    /**
     * GET /api/auth/business-profile
     * Hồ sơ doanh nghiệp mới nhất của chính mình (204 nếu chưa nộp).
     */
    @GetMapping("/business-profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BusinessProfileResponse> getMyBusinessProfile(
            @AuthenticationPrincipal UserDetails principal
    ) {
        String userId = authService.getUserIdByPhone(principal.getUsername());
        return businessProfileService.getMine(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = authService.getUserIdByPhone(userDetails.getUsername());
        return ResponseEntity.ok(authService.getMyProfile(userId));
    }
}
