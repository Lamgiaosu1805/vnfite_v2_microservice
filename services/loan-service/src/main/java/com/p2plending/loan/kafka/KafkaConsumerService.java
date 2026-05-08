package com.p2plending.loan.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
     * Listens to "payment.completed" events published by a future payment-service.
     * On receipt, transitions the corresponding loan status (REPAYING → REPAYING or COMPLETED).
     */
    @KafkaListener(
        topics   = "payment.completed",
        groupId  = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentCompleted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("Received payment.completed key={} partition={} offset={}",
                record.key(), record.partition(), record.offset());
        try {
            PaymentCompletedEvent event = objectMapper.readValue(record.value(), PaymentCompletedEvent.class);
            loanService.handlePaymentCompleted(event);
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            log.error("Malformed payment.completed message — skipping. key={} value={}",
                    record.key(), record.value(), e);
            ack.acknowledge();   // ACK to avoid infinite retry on poison-pill messages
        } catch (Exception e) {
            log.error("Failed to process payment.completed key={}: {}", record.key(), e.getMessage(), e);
            // Do not ACK — let the container retry according to error-handler config
        }
    }
}
