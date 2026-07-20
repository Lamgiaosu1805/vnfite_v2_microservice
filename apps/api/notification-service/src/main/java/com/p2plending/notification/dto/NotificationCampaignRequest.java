package com.p2plending.notification.dto;

import com.p2plending.notification.domain.enums.CampaignSendMode;
import com.p2plending.notification.domain.enums.CampaignType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class NotificationCampaignRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String body;

    @NotNull
    private CampaignType campaignType;

    /** null = tất cả segment; ngược lại APPROVED|PENDING|NONE|REJECTED. */
    private String segmentKycStatus;

    @NotNull
    private CampaignSendMode sendMode;

    /** Bắt buộc khi sendMode = SCHEDULED. */
    private LocalTime scheduledTime;
    private LocalDate startDate;
    private LocalDate endDate;

    private String createdBy;
}
