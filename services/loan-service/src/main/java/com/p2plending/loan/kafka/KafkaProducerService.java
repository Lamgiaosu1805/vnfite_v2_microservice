package com.p2plending.loan.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.kafka.event.LoanCreatedEvent;
import com.p2plending.loan.kafka.event.LoanFundedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private static final String TOPIC_LOAN_CREATED = "loan.created";
    private static final String TOPIC_LOAN_FUNDED  = "loan.funded";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishLoanCreated(LoanRequest loan) {
        LoanCreatedEvent event = LoanCreatedEvent.builder()
                .loanId(loan.getId())
                .borrowerId(loan.getBorrowerId())
                .amount(loan.getAmount())
                .interestRate(loan.getInterestRate())
                .termMonths(loan.getTermMonths())
                .purpose(loan.getPurpose())
                .createdAt(loan.getCreatedAt())
                .build();
        send(TOPIC_LOAN_CREATED, loan.getId().toString(), event);
    }

    public void publishLoanFunded(LoanRequest loan) {
        LoanFundedEvent event = LoanFundedEvent.builder()
                .loanId(loan.getId())
                .borrowerId(loan.getBorrowerId())
                .totalAmount(loan.getAmount())
                .fundedAt(LocalDateTime.now())
                .build();
        send(TOPIC_LOAN_FUNDED, loan.getId().toString(), event);
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
