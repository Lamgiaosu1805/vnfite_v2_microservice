package com.p2plending.matching.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.matching.domain.entity.MatchRecord;
import com.p2plending.matching.domain.entity.PendingLoan;
import com.p2plending.matching.kafka.event.MatchFoundEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private static final String TOPIC_MATCH_FOUND = "match.found";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishMatchFound(MatchRecord record, PendingLoan loan) {
        MatchFoundEvent event = MatchFoundEvent.builder()
                .loanId(record.getLoanId())
                .investorId(record.getInvestorId())
                .score(record.getScore())
                .loanAmount(loan.getAmount())
                .interestRate(loan.getInterestRate())
                .termMonths(loan.getTermMonths())
                .matchedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                .build();
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC_MATCH_FOUND, record.getInvestorId().toString(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish match.found loan={} investor={}: {}",
                                    record.getLoanId(), record.getInvestorId(), ex.getMessage());
                        } else {
                            log.debug("Published match.found loan={} investor={} score={}",
                                    record.getLoanId(), record.getInvestorId(), record.getScore());
                        }
                    });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialise MatchFoundEvent", e);
        }
    }
}
