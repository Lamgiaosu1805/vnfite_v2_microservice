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
import com.p2plending.cms.security.CmsPermissions;
import com.p2plending.cms.security.CmsRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminManagementService {

    private final CmsAdminUserRepository adminRepo;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGeneratorService usernameGenerator;

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    /** Thứ tự ưu tiên chọn nhãn hiển thị khi tài khoản mang nhiều vai trò. */
    private static final List<String> LABEL_PRIORITY = List.of(
            CmsRoles.ADMIN, CmsRoles.APPROVER, CmsRoles.APPRAISER, CmsRoles.FINANCE,
            CmsRoles.CUSTOMER_SUPPORT, CmsRoles.CONTENT, CmsRoles.HR, CmsRoles.OPS);
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
                .roles(new LinkedHashSet<>(Set.of(SUPER_ADMIN)))
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

        Set<String> roles = sanitizeRoles(request.getRoles());
        Set<String> permissions = sanitizePermissions(request.getPermissions());

        CmsAdminUser admin = adminRepo.save(CmsAdminUser.builder()
                .username(username)
                .email(request.getEmail())
                .fullName(request.getFullName())
                .password(passwordEncoder.encode(rawPassword))
                .role(primaryLabel(roles))
                .roles(roles)
                .permissions(permissions)
                .mustChangePassword(true)   // bắt đổi MK lần đầu
                .createdBy(createdById)
                .build());

        return CreateAdminResponse.builder()
                .id(admin.getId())
                .username(username)
                .email(admin.getEmail())
                .fullName(admin.getFullName())
                .role(admin.getRole())
                .roles(new ArrayList<>(admin.getRoles()))
                .permissions(new ArrayList<>(admin.getPermissions()))
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
    public AdminListResponse updateRole(String adminId, List<String> newRoles, String requesterId) {
        if (adminId.equals(requesterId)) {
            throw new IllegalArgumentException("Không thể tự thay đổi quyền của mình");
        }
        Set<String> roles = sanitizeRoles(newRoles);

        CmsAdminUser admin = adminRepo.findByIdAndIsDeletedFalse(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy admin"));
        if (SUPER_ADMIN.equals(admin.getRole())) {
            throw new IllegalArgumentException("Không thể thay đổi quyền tài khoản Super Admin");
        }

        admin.setRoles(roles);
        admin.setRole(primaryLabel(roles));
        return toListResponse(adminRepo.save(admin));
    }

    @Transactional
    public AdminListResponse updatePermissions(String adminId, List<String> newPermissions, String requesterId) {
        if (adminId.equals(requesterId)) {
            throw new IllegalArgumentException("Không thể tự thay đổi quyền của mình");
        }
        Set<String> permissions = sanitizePermissions(newPermissions);

        CmsAdminUser admin = adminRepo.findByIdAndIsDeletedFalse(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy admin"));
        if (SUPER_ADMIN.equals(admin.getRole())) {
            throw new IllegalArgumentException("Không thể thay đổi quyền tài khoản Super Admin");
        }

        admin.setPermissions(permissions);
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

    /** Chuẩn hoá & kiểm tra danh sách vai trò gán được (loại trùng, giữ thứ tự, chặn rỗng/không hợp lệ). */
    private Set<String> sanitizeRoles(List<String> input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Phải chọn ít nhất một vai trò");
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String r : input) {
            String role = r == null ? null : r.trim().toUpperCase();
            if (!CmsRoles.isValidAssignable(role)) {
                throw new IllegalArgumentException("Vai trò không hợp lệ: " + r);
            }
            roles.add(role);
        }
        return roles;
    }

    /** Chuẩn hoá & kiểm tra danh sách quyền lẻ (loại trùng, giữ thứ tự, chặn giá trị không hợp lệ). Rỗng/null hợp lệ. */
    private Set<String> sanitizePermissions(List<String> input) {
        if (input == null || input.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<String> permissions = new LinkedHashSet<>();
        for (String p : input) {
            String permission = p == null ? null : p.trim();
            if (!CmsPermissions.isValidAssignable(permission)) {
                throw new IllegalArgumentException("Quyền không hợp lệ: " + p);
            }
            permissions.add(permission);
        }
        return permissions;
    }

    /** Nhãn hiển thị đại diện khi tài khoản mang nhiều vai trò. */
    private String primaryLabel(Set<String> roles) {
        for (String candidate : LABEL_PRIORITY) {
            if (roles.contains(candidate)) return candidate;
        }
        return roles.iterator().next();
    }

    private AdminListResponse toListResponse(CmsAdminUser admin) {
        return AdminListResponse.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .email(admin.getEmail())
                .fullName(admin.getFullName())
                .role(admin.getRole())
                .roles(admin.getRoles() == null ? List.of() : new ArrayList<>(admin.getRoles()))
                .permissions(admin.getPermissions() == null ? List.of() : new ArrayList<>(admin.getPermissions()))
                .active(admin.isActive())
                .mustChangePassword(admin.isMustChangePassword())
                .totpEnabled(admin.isTotpEnabled())
                .createdAt(admin.getCreatedAt())
                .build();
    }
}
