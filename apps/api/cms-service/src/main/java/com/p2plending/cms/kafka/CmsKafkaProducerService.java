package com.p2plending.cms.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.cms.kafka.event.LoanReviewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CmsKafkaProducerService {

    private static final String TOPIC_LOAN_REVIEWED = "loan.reviewed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishLoanReviewed(LoanReviewedEvent event) {
        send(TOPIC_LOAN_REVIEWED, event.getLoanId(), event);
    }

    private void send(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish {} key={}: {}", topic, key, ex.getMessage());
                        } else {
                            log.info("Published {} key={} partition={} offset={}",
                                    topic, key,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Serialisation error for topic={} key={}", topic, key, e);
            throw new RuntimeException("Failed to serialise Kafka event", e);
        }
    }
}
