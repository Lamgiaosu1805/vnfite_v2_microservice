package com.p2plending.auth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_ip_blocks")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OtpIpBlock {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "ip_address", nullable = false, unique = true, length = 64)
    private String ipAddress;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "blocked_by", nullable = false, length = 100)
    private String blockedBy;

    @Column(name = "unblocked_by", length = 100)
    private String unblockedBy;

    @Column(name = "unblocked_at")
    private LocalDateTime unblockedAt;

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
