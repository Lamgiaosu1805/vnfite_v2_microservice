package com.p2plending.cms.controller;

import com.p2plending.cms.domain.entity.CmsAdminUser;
import com.p2plending.cms.domain.repository.CmsAdminUserRepository;
import com.p2plending.cms.dto.request.CmsLoginRequest;
import com.p2plending.cms.dto.request.TotpConfirmRequest;
import com.p2plending.cms.dto.request.TotpVerifyRequest;
import com.p2plending.cms.dto.response.CmsAuthResponse;
import com.p2plending.cms.dto.response.TotpPendingResponse;
import com.p2plending.cms.dto.response.TotpSetupResponse;
import com.p2plending.cms.exception.InvalidCredentialsException;
import com.p2plending.cms.service.CmsAuthService;
import com.p2plending.cms.service.CmsJwtService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cms/auth")
@RequiredArgsConstructor
public class CmsAuthController {

    private final CmsAuthService authService;
    private final CmsJwtService jwtService;
    private final CmsAdminUserRepository adminRepo;

    /** Bước 1: xác thực mật khẩu → nhận pendingToken */
    @PostMapping("/login")
    public ResponseEntity<TotpPendingResponse> login(@Valid @RequestBody CmsLoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Bước 2a: lấy secret + QR URI để thiết lập TOTP lần đầu */
    @GetMapping("/totp/setup-init")
    public ResponseEntity<TotpSetupResponse> totpSetupInit(
            @RequestHeader("Authorization") String authHeader) {
        CmsAdminUser admin = requirePendingAuth(authHeader);
        return ResponseEntity.ok(authService.initTotpSetup(admin));
    }

    /** Bước 2b: xác nhận mã OTP → kích hoạt 2FA + nhận accessToken đầy đủ */
    @PostMapping("/totp/setup-confirm")
    public ResponseEntity<CmsAuthResponse> totpSetupConfirm(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody TotpConfirmRequest request) {
        CmsAdminUser admin = requirePendingAuth(authHeader);
        return ResponseEntity.ok(authService.confirmTotpSetup(admin, request.getSecret(), request.getCode()));
    }

    /** Bước 2c: nhập mã OTP cho lần đăng nhập tiếp theo → nhận accessToken */
    @PostMapping("/totp/verify")
    public ResponseEntity<CmsAuthResponse> totpVerify(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody TotpVerifyRequest request) {
        CmsAdminUser admin = requirePendingAuth(authHeader);
        return ResponseEntity.ok(authService.verifyTotp(admin, request.getCode()));
    }

    // ─── Helper: xác thực pendingToken từ header ──────────────────────────────

    private CmsAdminUser requirePendingAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException("Thiếu token xác thực");
        }
        Claims claims;
        try {
            claims = jwtService.parse(authHeader.substring(7));
        } catch (Exception e) {
            throw new InvalidCredentialsException("Token không hợp lệ hoặc đã hết hạn");
        }
        if (!jwtService.isPendingToken(claims)) {
            throw new InvalidCredentialsException("Token không đúng loại");
        }
        return adminRepo.findByUsernameAndIsDeletedFalse(claims.getSubject())
                .filter(CmsAdminUser::isActive)
                .orElseThrow(() -> new InvalidCredentialsException("Không tìm thấy tài khoản"));
    }
}
