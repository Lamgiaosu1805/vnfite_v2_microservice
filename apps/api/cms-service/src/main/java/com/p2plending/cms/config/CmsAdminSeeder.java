package com.p2plending.cms.config;

import com.p2plending.cms.domain.entity.CmsAdminUser;
import com.p2plending.cms.domain.repository.CmsAdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CmsAdminSeeder implements ApplicationRunner {

    private final CmsAdminUserRepository adminRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${cms.admin.username:admin}")
    private String username;

    @Value("${cms.admin.password:Admin@123456}")
    private String password;

    @Value("${cms.admin.email:admin@p2plending.local}")
    private String email;

    @Value("${cms.admin.full-name:CMS Administrator}")
    private String fullName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminRepo.existsByUsername(username)) {
            return;
        }

        adminRepo.save(CmsAdminUser.builder()
                .username(username)
                .email(email)
                .fullName(fullName)
                .password(passwordEncoder.encode(password))
                .role("ADMIN")
                .build());
        log.warn("Seeded default CMS admin user '{}'. Change CMS_ADMIN_PASSWORD for non-local environments.", username);
    }
}
