package com.p2plending.auth.service;

import com.p2plending.auth.config.RedisNamespaceProperties;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.dto.response.InternalCustomerPasswordResetResponse;
import com.p2plending.auth.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class InternalCustomerAdminService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String DEVICE_SESSION_PREFIX = "device_session:";
    private static final String BIOMETRIC_CHALLENGE_PREFIX = "biometric_challenge:";
    private static final String LOGIN_FAIL_PREFIX = "login_fail:";
    private static final String LOGIN_LOCK_PREFIX = "login_lock:";
    private static final char[] PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final RedisNamespaceProperties redisNamespaceProperties;
    private final FcmTokenService fcmTokenService;

    @Transactional
    public InternalCustomerPasswordResetResponse resetPassword(String userId) {
        User user = findActiveUser(userId);
        String rawPassword = generatePassword();

        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setBiometricPublicKey(null);
        userRepository.save(user);
        revokeSessions(user);

        log.info("CMS admin reset customer password: userId={}", userId);
        return InternalCustomerPasswordResetResponse.builder()
                .userId(user.getId())
                .phone(user.getPhone())
                .generatedPassword(rawPassword)
                .build();
    }

    @Transactional
    public void resetDevice(String userId) {
        User user = findActiveUser(userId);
        user.setBiometricPublicKey(null);
        userRepository.save(user);
        revokeSessions(user);
        log.info("CMS admin reset customer device: userId={}", userId);
    }

    private User findActiveUser(String userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng"));
    }

    private void revokeSessions(User user) {
        String phone = user.getPhone();
        redisTemplate.delete(redisKey(REFRESH_TOKEN_PREFIX + phone));
        redisTemplate.delete(redisKey(DEVICE_SESSION_PREFIX + phone));
        redisTemplate.delete(redisKey(BIOMETRIC_CHALLENGE_PREFIX + phone));
        redisTemplate.delete(redisKey(LOGIN_FAIL_PREFIX + phone));
        redisTemplate.delete(redisKey(LOGIN_LOCK_PREFIX + phone));
        fcmTokenService.removeToken(user.getId());
    }

    private String redisKey(String key) {
        return redisNamespaceProperties.qualify(key);
    }

    private String generatePassword() {
        StringBuilder suffix = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            suffix.append(PASSWORD_CHARS[SECURE_RANDOM.nextInt(PASSWORD_CHARS.length)]);
        }
        return "Vnfite@" + suffix + SECURE_RANDOM.nextInt(10);
    }
}
