package com.p2plending.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.notification.kafka.event.ContractReadyEvent;
import com.p2plending.notification.kafka.event.DepositCompletedEvent;
import com.p2plending.notification.kafka.event.InvestmentRefundedEvent;
import com.p2plending.notification.kafka.event.LoanApprovedAwaitingBorrowerEvent;
import com.p2plending.notification.kafka.event.LoanDisbursedEvent;
import com.p2plending.notification.kafka.event.LoanRepaidEvent;
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
            topics = "payment.deposit_completed",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}"
    )
    public void onDepositCompleted(ConsumerRecord<String, String> record) {
        try {
            DepositCompletedEvent event = objectMapper.readValue(record.value(), DepositCompletedEvent.class);
            notificationService.notifyDepositCompleted(event);
        } catch (Exception ex) {
            log.error("Failed to process payment.deposit_completed key={}: {}", record.key(), ex.getMessage(), ex);
        }
    }

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

    @KafkaListener(
            topics = "contract.ready",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}"
    )
    public void onContractReady(ConsumerRecord<String, String> record) {
        try {
            ContractReadyEvent event = objectMapper.readValue(record.value(), ContractReadyEvent.class);
            notificationService.notifyContractReady(event);
        } catch (Exception ex) {
            log.error("Failed to process contract.ready key={}: {}", record.key(), ex.getMessage(), ex);
        }
    }

    @KafkaListener(
            topics = "loan.disbursed",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}"
    )
    public void onLoanDisbursed(ConsumerRecord<String, String> record) {
        try {
            LoanDisbursedEvent event = objectMapper.readValue(record.value(), LoanDisbursedEvent.class);
            notificationService.notifyLoanDisbursed(event);
        } catch (Exception ex) {
            log.error("Failed to process loan.disbursed key={}: {}", record.key(), ex.getMessage(), ex);
        }
    }

    @KafkaListener(
            topics = "investment.refunded",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}"
    )
    public void onInvestmentRefunded(ConsumerRecord<String, String> record) {
        try {
            InvestmentRefundedEvent event = objectMapper.readValue(record.value(), InvestmentRefundedEvent.class);
            notificationService.notifyInvestmentRefunded(event);
        } catch (Exception ex) {
            log.error("Failed to process investment.refunded key={}: {}", record.key(), ex.getMessage(), ex);
        }
    }

    @KafkaListener(
            topics = "loan.repayment_completed",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}"
    )
    public void onLoanRepaid(ConsumerRecord<String, String> record) {
        try {
            LoanRepaidEvent event = objectMapper.readValue(record.value(), LoanRepaidEvent.class);
            notificationService.notifyLoanRepaid(event);
        } catch (Exception ex) {
            log.error("Failed to process loan.repayment_completed key={}: {}", record.key(), ex.getMessage(), ex);
        }
    }
}
