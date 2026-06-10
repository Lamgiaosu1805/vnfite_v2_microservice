package com.p2plending.auth.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final String TOPIC_KYC_APPROVED   = "kyc.approved";
    private static final String TOPIC_USER_REGISTERED = "user.registered";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishUserRegistered(String userId, String phone) {
        try {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("userId", userId);
            data.put("phone", phone);
            data.put("registeredAt", LocalDateTime.now().toString());
            kafkaTemplate.send(TOPIC_USER_REGISTERED, userId.toString(), objectMapper.writeValueAsString(data))
                    .whenComplete((r, ex) -> {
                        if (ex != null) log.error("Failed to publish user.registered userId={}: {}", userId, ex.getMessage());
                    });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Serialisation error for user.registered userId={}", userId, e);
        }
    }

    /**
     * Publish khi CMS duyệt KYC thành công.
     * payment-service lắng nghe event này để tạo ví + VA TIKLUY.
     */
    public void publishKycApproved(String userId, String fullName, String cccdNumber) {
        try {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("userId", userId);
            data.put("fullName", fullName);
            data.put("cccdNumber", cccdNumber);
            data.put("approvedAt", LocalDateTime.now().toString());
            String payload = objectMapper.writeValueAsString(data);
            kafkaTemplate.send(TOPIC_KYC_APPROVED, userId, payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) log.error("Failed to publish kyc.approved userId={}: {}", userId, ex.getMessage());
                        else log.info("Published kyc.approved userId={}", userId);
                    });
        } catch (JsonProcessingException e) {
            log.error("Serialisation error for kyc.approved userId={}", userId, e);
        }
    }

    public void publishKycSubmitted(String userId, String documentId) {
        KycSubmittedEvent event = KycSubmittedEvent.builder()
                .userId(userId)
                .documentId(documentId)
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
