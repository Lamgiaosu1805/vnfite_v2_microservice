package com.p2plending.auth.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycSubmittedEvent {
    private String userId;
    private String documentId;
    private String docType;
    private LocalDateTime submittedAt;
}
