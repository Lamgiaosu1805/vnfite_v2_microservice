package com.p2plending.auth.controller;

import com.p2plending.auth.dto.request.CheckPhoneRequest;
import com.p2plending.auth.dto.request.KycSubmitRequest;
import com.p2plending.auth.dto.request.LoginRequest;
import com.p2plending.auth.dto.request.OtpVerifyRequest;
import com.p2plending.auth.dto.request.RefreshTokenRequest;
import com.p2plending.auth.dto.request.RegisterRequest;
import com.p2plending.auth.dto.response.AuthResponse;
import com.p2plending.auth.dto.response.KycDocumentResponse;
import com.p2plending.auth.dto.response.RegisterInitResponse;
import com.p2plending.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
     * POST /api/auth/kyc/submit
     * Yêu cầu đăng nhập. EKYC có thể thực hiện bất kỳ lúc nào.
     */
    @PostMapping("/kyc/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<KycDocumentResponse> submitKyc(
            @Valid @RequestBody KycSubmitRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        Long userId = authService.getUserIdByPhone(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.submitKyc(userId, request));
    }
}
