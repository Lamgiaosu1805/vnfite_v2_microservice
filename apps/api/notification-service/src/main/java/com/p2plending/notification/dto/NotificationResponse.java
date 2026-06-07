package com.p2plending.notification.dto;

import com.p2plending.notification.domain.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    private String id;
    private String title;
    private String message;
    private NotificationType type;
    private String channel;
    private String referenceId;
    private String referenceType;
    private boolean read;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
