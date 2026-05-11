package com.p2plending.auth.controller;

import com.p2plending.auth.dto.request.CheckPhoneRequest;
import com.p2plending.auth.dto.request.KycInitRequest;
import com.p2plending.auth.dto.request.KycVerifyRequest;
import com.p2plending.auth.dto.request.LoginRequest;
import com.p2plending.auth.dto.request.OtpVerifyRequest;
import com.p2plending.auth.dto.request.RefreshTokenRequest;
import com.p2plending.auth.dto.request.RegisterRequest;
import com.p2plending.auth.dto.response.AuthResponse;
import com.p2plending.auth.dto.response.KycSubmissionResponse;
import com.p2plending.auth.dto.response.RegisterInitResponse;
import com.p2plending.auth.service.AuthService;
import com.p2plending.auth.service.KycService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final KycService  kycService;

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
    public ResponseEntity<RegisterInitResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.registerInit(request));
    }

    /**
     * POST /api/auth/register/verify
     * Bước 3: Xác thực OTP → tạo tài khoản → trả về JWT.
     */
    @PostMapping("/register/verify")
    public ResponseEntity<AuthResponse> registerVerify(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerVerify(request));
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
     * POST /api/auth/kyc/init
     * Bước 1: Kiểm tra CCCD, upload ảnh, gửi OTP.
     */
    @PostMapping(value = "/kyc/init", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> kycInit(
            @Valid @ModelAttribute KycInitRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        String userId = authService.getUserIdByPhone(principal.getUsername());
        return ResponseEntity.ok(kycService.initKyc(userId, request));
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
}
