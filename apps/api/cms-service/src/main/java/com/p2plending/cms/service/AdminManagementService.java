package com.p2plending.cms.service;

import com.p2plending.cms.domain.entity.CmsAdminUser;
import com.p2plending.cms.domain.repository.CmsAdminUserRepository;
import com.p2plending.cms.dto.request.ChangePasswordRequest;
import com.p2plending.cms.dto.request.CreateAdminRequest;
import com.p2plending.cms.dto.request.SetupRequest;
import com.p2plending.cms.dto.response.AdminListResponse;
import com.p2plending.cms.dto.response.CreateAdminResponse;
import com.p2plending.cms.dto.response.ResetAdminPasswordResponse;
import com.p2plending.cms.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminManagementService {

    private final CmsAdminUserRepository adminRepo;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGeneratorService usernameGenerator;

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final List<String> EDITABLE_ROLES = List.of("ADMIN", "OPS");
    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    // ─── Setup ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isSetupRequired() {
        return !adminRepo.existsByIsDeletedFalse();
    }

    @Transactional
    public void setup(SetupRequest request) {
        if (!isSetupRequired()) {
            throw new IllegalStateException("Hệ thống đã được cài đặt");
        }
        adminRepo.save(CmsAdminUser.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(SUPER_ADMIN)
                .mustChangePassword(false)
                .build());
    }

    // ─── Create Admin ─────────────────────────────────────────────────────────

    @Transactional
    public CreateAdminResponse createAdmin(CreateAdminRequest request, String createdById) {
        String username = usernameGenerator.generate(request.getFullName());
        String rawPassword = generatePassword();

        if (adminRepo.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email đã được sử dụng");
        }

        CmsAdminUser admin = adminRepo.save(CmsAdminUser.builder()
                .username(username)
                .email(request.getEmail())
                .fullName(request.getFullName())
                .password(passwordEncoder.encode(rawPassword))
                .role(request.getRole())
                .mustChangePassword(true)   // bắt đổi MK lần đầu
                .createdBy(createdById)
                .build());

        return CreateAdminResponse.builder()
                .id(admin.getId())
                .username(username)
                .email(admin.getEmail())
                .fullName(admin.getFullName())
                .role(admin.getRole())
                .generatedPassword(rawPassword)  // trả về 1 lần duy nhất
                .build();
    }

    // ─── List Admins ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminListResponse> listAdmins() {
        return adminRepo.findAllByIsDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toListResponse)
                .toList();
    }

    // ─── Toggle Active ────────────────────────────────────────────────────────

    @Transactional
    public void toggleActive(String adminId, String requesterId) {
        if (adminId.equals(requesterId)) {
            throw new IllegalArgumentException("Không thể tự khoá tài khoản của mình");
        }
        CmsAdminUser admin = adminRepo.findByIdAndIsDeletedFalse(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy admin"));
        if (SUPER_ADMIN.equals(admin.getRole())) {
            throw new IllegalArgumentException("Không thể khoá tài khoản Super Admin");
        }
        admin.setActive(!admin.isActive());
        adminRepo.save(admin);
    }

    @Transactional
    public AdminListResponse updateRole(String adminId, String newRole, String requesterId) {
        if (adminId.equals(requesterId)) {
            throw new IllegalArgumentException("Không thể tự thay đổi quyền của mình");
        }
        if (!EDITABLE_ROLES.contains(newRole)) {
            throw new IllegalArgumentException("Role phải là ADMIN hoặc OPS");
        }

        CmsAdminUser admin = adminRepo.findByIdAndIsDeletedFalse(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy admin"));
        if (SUPER_ADMIN.equals(admin.getRole())) {
            throw new IllegalArgumentException("Không thể thay đổi quyền tài khoản Super Admin");
        }

        admin.setRole(newRole);
        return toListResponse(adminRepo.save(admin));
    }

    // ─── Change Password ──────────────────────────────────────────────────────

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        CmsAdminUser admin = adminRepo.findByUsernameAndIsDeletedFalse(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy admin"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), admin.getPassword())) {
            throw new InvalidCredentialsException("Mật khẩu hiện tại không đúng");
        }
        admin.setPassword(passwordEncoder.encode(request.getNewPassword()));
        admin.setMustChangePassword(false);
        adminRepo.save(admin);
    }

    // ─── Reset Password / TOTP ───────────────────────────────────────────────

    @Transactional
    public ResetAdminPasswordResponse resetPassword(String adminId, String requesterId) {
        if (adminId.equals(requesterId)) {
            throw new IllegalArgumentException("Không thể tự reset mật khẩu của mình tại màn quản trị");
        }
        CmsAdminUser admin = adminRepo.findByIdAndIsDeletedFalse(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy admin"));
        String rawPassword = generatePassword();
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setMustChangePassword(true);
        adminRepo.save(admin);
        return ResetAdminPasswordResponse.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .fullName(admin.getFullName())
                .generatedPassword(rawPassword)
                .build();
    }

    @Transactional
    public void resetTotp(String adminId, String requesterId) {
        if (adminId.equals(requesterId)) {
            throw new IllegalArgumentException("Không thể tự reset TOTP của mình tại màn quản trị");
        }
        CmsAdminUser admin = adminRepo.findByIdAndIsDeletedFalse(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy admin"));
        admin.setTotpSecret(null);
        admin.setTotpEnabled(false);
        adminRepo.save(admin);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private AdminListResponse toListResponse(CmsAdminUser admin) {
        return AdminListResponse.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .email(admin.getEmail())
                .fullName(admin.getFullName())
                .role(admin.getRole())
                .active(admin.isActive())
                .mustChangePassword(admin.isMustChangePassword())
                .totpEnabled(admin.isTotpEnabled())
                .createdAt(admin.getCreatedAt())
                .build();
    }
}
