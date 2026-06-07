package com.p2plending.auth.service;

import com.p2plending.auth.domain.entity.UserFcmToken;
import com.p2plending.auth.domain.repository.UserFcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmTokenService {

    private final UserFcmTokenRepository fcmTokenRepository;

    /**
     * Lưu hoặc cập nhật FCM token của user.
     * Gọi sau khi mobile nhận token từ Firebase SDK.
     */
    @Transactional
    public void saveToken(String userId, String fcmToken, String deviceKey) {
        UserFcmToken token = fcmTokenRepository.findByUserId(userId)
                .orElse(UserFcmToken.builder().userId(userId).build());

        token.setFcmToken(fcmToken);
        token.setDeviceKey(deviceKey);
        fcmTokenRepository.save(token);

        log.info("FCM token saved for user={} device={}", userId, deviceKey);
    }

    /**
     * Xóa FCM token khi user đăng xuất — tránh gửi push đến thiết bị đã logout.
     */
    @Transactional
    public void removeToken(String userId) {
        fcmTokenRepository.findByUserId(userId).ifPresent(token -> {
            fcmTokenRepository.delete(token);
            log.info("FCM token removed for user={}", userId);
        });
    }

    /**
     * Lấy FCM token để gửi push notification (dùng cho internal API).
     */
    @Transactional(readOnly = true)
    public Optional<String> getToken(String userId) {
        return fcmTokenRepository.findByUserId(userId).map(UserFcmToken::getFcmToken);
    }
}
