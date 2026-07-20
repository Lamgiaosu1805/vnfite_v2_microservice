package com.p2plending.notification.domain.entity;

import com.p2plending.notification.domain.enums.CampaignSendMode;
import com.p2plending.notification.domain.enums.CampaignStatus;
import com.p2plending.notification.domain.enums.CampaignType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "notification_campaigns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_type", nullable = false, length = 20)
    private CampaignType campaignType;

    /** Lọc segment theo trạng thái KYC bên auth-service. Null = tất cả. */
    @Column(name = "segment_kyc_status", length = 20)
    private String segmentKycStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "send_mode", nullable = false, length = 20)
    private CampaignSendMode sendMode;

    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status;

    @Column(name = "last_sent_date")
    private LocalDate lastSentDate;

    @Column(name = "total_sent_count", nullable = false)
    @Builder.Default
    private int totalSentCount = 0;

    @Column(name = "created_by", length = 36)
    private String createdBy;

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
