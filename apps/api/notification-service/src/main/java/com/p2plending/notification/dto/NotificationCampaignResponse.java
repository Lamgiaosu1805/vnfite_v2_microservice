package com.p2plending.notification.dto;

import com.p2plending.notification.domain.enums.CampaignSendMode;
import com.p2plending.notification.domain.enums.CampaignStatus;
import com.p2plending.notification.domain.enums.CampaignType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class NotificationCampaignResponse {
    private String id;
    private String title;
    private String body;
    private CampaignType campaignType;
    private String segmentKycStatus;
    private CampaignSendMode sendMode;
    private LocalTime scheduledTime;
    private LocalDate startDate;
    private LocalDate endDate;
    private CampaignStatus status;
    private LocalDate lastSentDate;
    private int totalSentCount;
    private String createdBy;
    private LocalDateTime createdAt;
}
