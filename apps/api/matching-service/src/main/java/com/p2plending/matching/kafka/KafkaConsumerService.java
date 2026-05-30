package com.p2plending.matching.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.matching.kafka.event.LoanCreatedEvent;
import com.p2plending.matching.kafka.event.LoanFundedEvent;
import com.p2plending.matching.service.MatchingService;
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

    private final MatchingService matchingService;
    private final ObjectMapper    objectMapper;

    /**
     * Triggers immediate matching when a new loan is created.
     */
    @KafkaListener(
        topics = "loan.created",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleLoanCreated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("Received loan.created key={}", record.key());
        try {
            LoanCreatedEvent event = objectMapper.readValue(record.value(), LoanCreatedEvent.class);
            matchingService.onLoanCreated(event);
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            log.error("Malformed loan.created — skipping. key={}", record.key(), e);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing loan.created key={}: {}", record.key(), e.getMessage(), e);
            // no ACK → container retries
        }
    }

    /**
     * Marks loan as fully funded so the scheduler skips it on next run.
     * Also expires all PENDING/NOTIFIED matches for that loan.
     */
    @KafkaListener(
        topics = "loan.funded",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleLoanFunded(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("Received loan.funded key={}", record.key());
        try {
            LoanFundedEvent event = objectMapper.readValue(record.value(), LoanFundedEvent.class);
            matchingService.onLoanFunded(event.getLoanId());
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            log.error("Malformed loan.funded — skipping. key={}", record.key(), e);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing loan.funded key={}: {}", record.key(), e.getMessage(), e);
        }
    }
}
