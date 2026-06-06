package com.p2plending.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.notification.kafka.event.LoanApprovedAwaitingBorrowerEvent;
import com.p2plending.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @KafkaListener(
            topics = "loan.approved.awaiting_borrower",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}"
    )
    public void onLoanApprovedAwaitingBorrower(ConsumerRecord<String, String> record) {
        try {
            LoanApprovedAwaitingBorrowerEvent event = objectMapper.readValue(
                    record.value(), LoanApprovedAwaitingBorrowerEvent.class);
            notificationService.notifyLoanApprovedAwaitingBorrower(event);
        } catch (Exception ex) {
            log.error("Failed to process loan.approved.awaiting_borrower key={}: {}",
                    record.key(), ex.getMessage(), ex);
        }
    }
}
