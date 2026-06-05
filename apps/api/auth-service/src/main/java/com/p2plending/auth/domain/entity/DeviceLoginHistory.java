package com.p2plending.auth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "device_login_history",
    indexes = {
        @Index(name = "idx_dlh_user_id",    columnList = "user_id"),
        @Index(name = "idx_dlh_device_key", columnList = "device_key"),
        @Index(name = "idx_dlh_login_at",   columnList = "login_at"),
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DeviceLoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "device_key", nullable = false, length = 100)
    private String deviceKey;

    @Column(name = "device_name", nullable = false, length = 255)
    private String deviceName;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform;

    @Column(name = "login_at", nullable = false, updatable = false)
    private LocalDateTime loginAt;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
