package com.p2plending.cms.service;

import com.p2plending.cms.domain.entity.CmsAdminUser;
import com.p2plending.cms.domain.repository.CmsAdminUserRepository;
import com.p2plending.cms.dto.request.CmsLoginRequest;
import com.p2plending.cms.dto.response.CmsAdminResponse;
import com.p2plending.cms.dto.response.CmsAuthResponse;
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

    @Transactional(readOnly = true)
    public CmsAuthResponse login(CmsLoginRequest request) {
        CmsAdminUser admin = adminRepo.findByUsernameAndIsDeletedFalse(request.getUsername())
                .filter(CmsAdminUser::isActive)
                .orElseThrow(() -> new InvalidCredentialsException("Tên đăng nhập hoặc mật khẩu không đúng"));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new InvalidCredentialsException("Tên đăng nhập hoặc mật khẩu không đúng");
        }

        return CmsAuthResponse.builder()
                .accessToken(jwtService.generateAccessToken(admin))
                .expiresIn(jwtService.getAccessTokenExpiry())
                .admin(toResponse(admin))
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
