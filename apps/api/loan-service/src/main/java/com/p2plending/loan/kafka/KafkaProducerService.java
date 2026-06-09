package com.p2plending.loan.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.loan.domain.entity.LoanContract;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.kafka.event.ContractReadyEvent;
import com.p2plending.loan.kafka.event.LoanApprovedAwaitingBorrowerEvent;
import com.p2plending.loan.kafka.event.LoanCreatedEvent;
import com.p2plending.loan.kafka.event.LoanDisbursedEvent;
import com.p2plending.loan.kafka.event.LoanFundedEvent;
import com.p2plending.loan.kafka.event.LoanSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private static final String TOPIC_LOAN_SUBMITTED = "loan.submitted";
    private static final String TOPIC_LOAN_CREATED   = "loan.created";
    private static final String TOPIC_LOAN_FUNDED    = "loan.funded";
    private static final String TOPIC_LOAN_APPROVED_AWAITING_BORROWER = "loan.approved.awaiting_borrower";
    private static final String TOPIC_CONTRACT_READY = "contract.ready";
    private static final String TOPIC_LOAN_DISBURSED = "loan.disbursed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** Published when borrower submits — triggers CMS underwriting queue. */
    public void publishLoanSubmitted(LoanRequest loan) {
        LoanSubmittedEvent event = LoanSubmittedEvent.builder()
                .loanId(loan.getId())
                .loanCode(loan.getLoanCode())
                .borrowerId(loan.getBorrowerId())
                .amount(loan.getAmount())
                .termMonths(loan.getTermMonths())
                .purpose(loan.getPurpose())
                .referredBy(loan.getReferredBy())
                .monthlyIncome(loan.getMonthlyIncome())
                .occupation(loan.getOccupation())
                .currentAddress(loan.getCurrentAddress())
                .submittedAt(loan.getCreatedAt())
                .build();
        send(TOPIC_LOAN_SUBMITTED, loan.getId(), event);
    }

    /** Published after CMS approves — triggers matching-service. */
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
        send(TOPIC_LOAN_CREATED, loan.getId(), event);
    }

    public void publishLoanFunded(LoanRequest loan) {
        LoanFundedEvent event = LoanFundedEvent.builder()
                .loanId(loan.getId())
                .borrowerId(loan.getBorrowerId())
                .totalAmount(loan.getAmount())
                .fundedAt(LocalDateTime.now())
                .build();
        send(TOPIC_LOAN_FUNDED, loan.getId(), event);
    }

    public void publishLoanApprovedAwaitingBorrower(LoanRequest loan) {
        LoanApprovedAwaitingBorrowerEvent event = LoanApprovedAwaitingBorrowerEvent.builder()
                .loanId(loan.getId())
                .loanCode(loan.getLoanCode())
                .borrowerId(loan.getBorrowerId())
                .approvedAmount(loan.getAmount())
                .approvedInterestRate(loan.getInterestRate())
                .termMonths(loan.getTermMonths())
                .reviewedBy(loan.getReviewedBy())
                .approvedAt(loan.getReviewedAt() != null ? loan.getReviewedAt() : LocalDateTime.now())
                .build();
        send(TOPIC_LOAN_APPROVED_AWAITING_BORROWER, loan.getId(), event);
    }

    /** Published when the borrower's loan agreement is ready to sign (loan just FUNDED). */
    public void publishContractReady(LoanRequest loan, LoanContract contract) {
        ContractReadyEvent event = ContractReadyEvent.builder()
                .loanId(loan.getId())
                .loanCode(loan.getLoanCode())
                .borrowerId(loan.getBorrowerId())
                .contractId(contract.getId())
                .amount(loan.getAmount())
                .interestRate(loan.getInterestRate())
                .termMonths(loan.getTermMonths())
                .issuedAt(contract.getIssuedAt() != null ? contract.getIssuedAt() : LocalDateTime.now())
                .build();
        send(TOPIC_CONTRACT_READY, loan.getId(), event);
    }

    /** Published when OPS disburses funds to the borrower (loan → DISBURSED). */
    public void publishLoanDisbursed(LoanRequest loan, List<String> investorIds) {
        LoanDisbursedEvent event = LoanDisbursedEvent.builder()
                .loanId(loan.getId())
                .loanCode(loan.getLoanCode())
                .borrowerId(loan.getBorrowerId())
                .amount(loan.getAmount())
                .disbursedAt(loan.getDisbursedAt() != null ? loan.getDisbursedAt() : LocalDateTime.now())
                .investorIds(investorIds)
                .build();
        send(TOPIC_LOAN_DISBURSED, loan.getId(), event);
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
