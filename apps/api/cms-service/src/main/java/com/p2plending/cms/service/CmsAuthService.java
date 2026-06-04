package com.p2plending.cms.service;

import com.p2plending.cms.domain.entity.CmsAdminUser;
import com.p2plending.cms.domain.repository.CmsAdminUserRepository;
import com.p2plending.cms.dto.request.CmsLoginRequest;
import com.p2plending.cms.dto.response.CmsAdminResponse;
import com.p2plending.cms.dto.response.CmsAuthResponse;
import com.p2plending.cms.dto.response.TotpPendingResponse;
import com.p2plending.cms.dto.response.TotpSetupResponse;
import com.p2plending.cms.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CmsAuthService {

    private final CmsAdminUserRepository adminRepo;
    private final PasswordEncoder passwordEncoder;
    private final CmsJwtService jwtService;
    private final TotpService totpService;

    // ─── Bước 1: xác thực mật khẩu → trả pendingToken ────────────────────────

    @Transactional(readOnly = true)
    public TotpPendingResponse login(CmsLoginRequest request) {
        CmsAdminUser admin = adminRepo.findByUsernameAndIsDeletedFalse(request.getUsername())
                .filter(CmsAdminUser::isActive)
                .orElseThrow(() -> new InvalidCredentialsException("Tên đăng nhập hoặc mật khẩu không đúng"));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new InvalidCredentialsException("Tên đăng nhập hoặc mật khẩu không đúng");
        }

        return TotpPendingResponse.builder()
                .pendingToken(jwtService.generatePendingToken(admin.getUsername(), admin.getId()))
                .totpEnabled(admin.isTotpEnabled())
                .build();
    }

    // ─── Bước 2a: khởi tạo thiết lập TOTP lần đầu ────────────────────────────

    @Transactional(readOnly = true)
    public TotpSetupResponse initTotpSetup(CmsAdminUser admin) {
        if (admin.isTotpEnabled()) {
            throw new IllegalStateException("Xác thực 2 lớp đã được kích hoạt cho tài khoản này");
        }
        String secret = totpService.generateSecret();
        return TotpSetupResponse.builder()
                .secret(secret)
                .otpAuthUrl(totpService.getOtpAuthUrl(secret, admin.getUsername()))
                .build();
    }

    // ─── Bước 2b: xác nhận mã OTP để kích hoạt TOTP ─────────────────────────

    @Transactional
    public CmsAuthResponse confirmTotpSetup(CmsAdminUser admin, String secret, String code) {
        if (admin.isTotpEnabled()) {
            throw new IllegalStateException("Xác thực 2 lớp đã được kích hoạt");
        }
        if (!totpService.verifyCode(secret, code)) {
            throw new InvalidCredentialsException("Mã OTP không đúng. Vui lòng kiểm tra lại ứng dụng xác thực.");
        }
        // Reload để tránh stale entity trong transaction readOnly cha
        CmsAdminUser managed = adminRepo.findByUsernameAndIsDeletedFalse(admin.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Không tìm thấy admin"));
        managed.setTotpSecret(secret);
        managed.setTotpEnabled(true);
        adminRepo.save(managed);
        return buildAuthResponse(managed);
    }

    // ─── Bước 2c: xác thực TOTP khi đăng nhập (đã thiết lập trước đó) ────────

    @Transactional(readOnly = true)
    public CmsAuthResponse verifyTotp(CmsAdminUser admin, String code) {
        if (!admin.isTotpEnabled() || admin.getTotpSecret() == null) {
            throw new IllegalStateException("Xác thực 2 lớp chưa được thiết lập");
        }
        if (!totpService.verifyCode(admin.getTotpSecret(), code)) {
            throw new InvalidCredentialsException("Mã OTP không đúng. Vui lòng kiểm tra lại ứng dụng xác thực.");
        }
        return buildAuthResponse(admin);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private CmsAuthResponse buildAuthResponse(CmsAdminUser admin) {
        return CmsAuthResponse.builder()
                .accessToken(jwtService.generateAccessToken(admin))
                .expiresIn(jwtService.getAccessTokenExpiry())
                .admin(toResponse(admin))
                .mustChangePassword(admin.isMustChangePassword())
                .build();
    }

    private CmsAdminResponse toResponse(CmsAdminUser admin) {
        return CmsAdminResponse.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .email(admin.getEmail())
                .fullName(admin.getFullName())
                .role(admin.getRole())
                .build();
    }
}
