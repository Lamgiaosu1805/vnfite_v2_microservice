package com.p2plending.loan.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.loan.kafka.event.LoanReviewedEvent;
import com.p2plending.loan.kafka.event.PaymentCompletedEvent;
import com.p2plending.loan.service.LoanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final LoanService  loanService;
    private final ObjectMapper objectMapper;

    /**
     * CMS admin approves or rejects a loan.
     * On APPROVE: sets interest_rate, transitions to ACTIVE, publishes loan.created for matching.
     * On REJECT:  sets rejection_reason, transitions to REJECTED.
     */
    @KafkaListener(
        topics   = "loan.reviewed",
        groupId  = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleLoanReviewed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("Received loan.reviewed key={}", record.key());
        try {
            LoanReviewedEvent event = objectMapper.readValue(record.value(), LoanReviewedEvent.class);
            loanService.handleLoanReviewed(event);
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            log.error("Malformed loan.reviewed — skipping. key={}", record.key(), e);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process loan.reviewed key={}: {}", record.key(), e.getMessage(), e);
        }
    }

    /** Future payment-service integration. */
    @KafkaListener(
        topics   = "payment.completed",
        groupId  = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentCompleted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("Received payment.completed key={}", record.key());
        try {
            PaymentCompletedEvent event = objectMapper.readValue(record.value(), PaymentCompletedEvent.class);
            loanService.handlePaymentCompleted(event);
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            log.error("Malformed payment.completed — skipping. key={}", record.key(), e);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment.completed key={}: {}", record.key(), e.getMessage(), e);
        }
    }
}
