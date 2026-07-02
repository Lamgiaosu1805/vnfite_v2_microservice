package com.p2plending.payment.domain.entity;

import com.p2plending.payment.domain.enums.WalletOwnerType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    @Builder.Default
    private WalletOwnerType ownerType = WalletOwnerType.PERSONAL;

    @Column(name = "vnf_account_no", nullable = false, unique = true, length = 20)
    private String vnfAccountNo;

    @Column(name = "locked_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal lockedBalance = BigDecimal.ZERO;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
