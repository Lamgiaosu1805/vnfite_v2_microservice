package com.p2plending.payment.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "withdrawal_audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "withdrawal_id", nullable = false, length = 36)
    private String withdrawalId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    /** userId / adminId / 'SYSTEM' */
    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @Column(name = "actor_type", nullable = false, length = 10)
    private String actorType;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));

    public static WithdrawalAuditLog system(String withdrawalId, String from, String to, String note) {
        return WithdrawalAuditLog.builder()
                .withdrawalId(withdrawalId)
                .fromStatus(from)
                .toStatus(to)
                .actor("SYSTEM")
                .actorType("SYSTEM")
                .note(note)
                .build();
    }

    public static WithdrawalAuditLog byUser(String withdrawalId, String from, String to,
                                            String userId, String note) {
        return WithdrawalAuditLog.builder()
                .withdrawalId(withdrawalId)
                .fromStatus(from)
                .toStatus(to)
                .actor(userId)
                .actorType("USER")
                .note(note)
                .build();
    }

    public static WithdrawalAuditLog byAdmin(String withdrawalId, String from, String to,
                                             String adminId, String note) {
        return WithdrawalAuditLog.builder()
                .withdrawalId(withdrawalId)
                .fromStatus(from)
                .toStatus(to)
                .actor(adminId)
                .actorType("ADMIN")
                .note(note)
                .build();
    }
}
