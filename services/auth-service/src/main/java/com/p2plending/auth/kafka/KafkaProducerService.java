package com.p2plending.auth.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.auth.domain.enums.DocType;
import com.p2plending.auth.kafka.event.KycSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private static final String TOPIC_KYC_SUBMITTED  = "kyc.submitted";
    private static final String TOPIC_USER_REGISTERED = "user.registered";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishUserRegistered(Long userId, String email, String fullName, String role) {
        try {
            String payload = objectMapper.writeValueAsString(
                java.util.Map.of("userId", userId, "email", email, "fullName", fullName, "role", role,
                                 "registeredAt", java.time.LocalDateTime.now().toString()));
            kafkaTemplate.send(TOPIC_USER_REGISTERED, userId.toString(), payload)
                    .whenComplete((r, ex) -> {
                        if (ex != null) log.error("Failed to publish user.registered userId={}: {}", userId, ex.getMessage());
                    });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Serialisation error for user.registered userId={}", userId, e);
        }
    }

    public void publishKycSubmitted(Long userId, Long documentId, DocType docType) {
        KycSubmittedEvent event = KycSubmittedEvent.builder()
                .userId(userId)
                .documentId(documentId)
                .docType(docType.name())
                .submittedAt(LocalDateTime.now())
                .build();
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC_KYC_SUBMITTED, userId.toString(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish kyc.submitted for user={}: {}", userId, ex.getMessage());
                        } else {
                            log.info("Published kyc.submitted user={} partition={} offset={}",
                                    userId,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Serialisation error for KycSubmittedEvent user={}", userId, e);
            throw new RuntimeException("Failed to serialise KYC event", e);
        }
    }
}
