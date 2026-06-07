package com.p2plending.auth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Lưu FCM push token của thiết bị đang active.
 * 1 user = 1 thiết bị active → user_id là PK, UPSERT mỗi khi token thay đổi.
 */
@Entity
@Table(name = "user_fcm_tokens")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserFcmToken {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "fcm_token", nullable = false, columnDefinition = "TEXT")
    private String fcmToken;

    @Column(name = "device_key", length = 100)
    private String deviceKey;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
