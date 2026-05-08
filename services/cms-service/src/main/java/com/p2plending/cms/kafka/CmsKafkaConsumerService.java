package com.p2plending.cms.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.cms.domain.entity.CmsLoan;
import com.p2plending.cms.domain.entity.CmsUser;
import com.p2plending.cms.domain.entity.DailyStat;
import com.p2plending.cms.domain.repository.CmsLoanRepository;
import com.p2plending.cms.domain.repository.CmsUserRepository;
import com.p2plending.cms.domain.repository.DailyStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.p2plending.cms.config.CacheConfig.CACHE_DASHBOARD_CHART;
import static com.p2plending.cms.config.CacheConfig.CACHE_DASHBOARD_STATS;

@Service
@RequiredArgsConstructor
@Slf4j
public class CmsKafkaConsumerService {

    private final CmsUserRepository  userRepo;
    private final CmsLoanRepository  loanRepo;
    private final DailyStatRepository statRepo;
    private final ObjectMapper        mapper;

    // ── user.registered ──────────────────────────────────────────

    @KafkaListener(topics = "user.registered", groupId = "${spring.kafka.consumer.group-id}",
                   containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    @CacheEvict(value = {CACHE_DASHBOARD_STATS, CACHE_DASHBOARD_CHART}, allEntries = true)
    public void onUserRegistered(ConsumerRecord<String, String> rec, Acknowledgment ack) {
        process(rec, ack, "user.registered", node -> {
            Long userId = node.get("userId").asLong();
            if (!userRepo.existsById(userId)) {
                userRepo.save(CmsUser.builder()
                        .userId(userId)
                        .email(node.get("email").asText())
                        .fullName(node.get("fullName").asText())
                        .role(node.get("role").asText())
                        .createdAt(LocalDateTime.now())
                        .build());
                incrementDailyStat(LocalDate.now(), 1, 0, 0, BigDecimal.ZERO);
                log.debug("CMS: user registered userId={}", userId);
            }
        });
    }

    // ── kyc.submitted ────────────────────────────────────────────

    @KafkaListener(topics = "kyc.submitted", groupId = "${spring.kafka.consumer.group-id}",
                   containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    @CacheEvict(value = CACHE_DASHBOARD_STATS, allEntries = true)
    public void onKycSubmitted(ConsumerRecord<String, String> rec, Acknowledgment ack) {
        process(rec, ack, "kyc.submitted", node -> {
            Long userId = node.get("userId").asLong();
            userRepo.findById(userId).ifPresent(u -> {
                u.setKycStatus("PENDING");
                userRepo.save(u);
                log.debug("CMS: KYC submitted userId={}", userId);
            });
        });
    }

    // ── loan.created ─────────────────────────────────────────────

    @KafkaListener(topics = "loan.created", groupId = "${spring.kafka.consumer.group-id}",
                   containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    @CacheEvict(value = {CACHE_DASHBOARD_STATS, CACHE_DASHBOARD_CHART}, allEntries = true)
    public void onLoanCreated(ConsumerRecord<String, String> rec, Acknowledgment ack) {
        process(rec, ack, "loan.created", node -> {
            Long loanId = node.get("loanId").asLong();
            if (!loanRepo.existsById(loanId)) {
                BigDecimal amount = new BigDecimal(node.get("amount").asText());
                loanRepo.save(CmsLoan.builder()
                        .loanId(loanId)
                        .borrowerId(node.get("borrowerId").asLong())
                        .amount(amount)
                        .interestRate(new BigDecimal(node.get("interestRate").asText()))
                        .termMonths(node.get("termMonths").asInt())
                        .purpose(node.has("purpose") ? node.get("purpose").asText() : "")
                        .status("PENDING")
                        .createdAt(LocalDateTime.now())
                        .build());
                incrementDailyStat(LocalDate.now(), 0, 1, 0, amount);
                log.debug("CMS: loan created loanId={}", loanId);
            }
        });
    }

    // ── loan.funded ──────────────────────────────────────────────

    @KafkaListener(topics = "loan.funded", groupId = "${spring.kafka.consumer.group-id}",
                   containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    @CacheEvict(value = {CACHE_DASHBOARD_STATS, CACHE_DASHBOARD_CHART}, allEntries = true)
    public void onLoanFunded(ConsumerRecord<String, String> rec, Acknowledgment ack) {
        process(rec, ack, "loan.funded", node -> {
            Long loanId = node.get("loanId").asLong();
            loanRepo.findById(loanId).ifPresent(l -> {
                l.setStatus("FUNDED");
                loanRepo.save(l);
                incrementDailyStat(LocalDate.now(), 0, 0, 1, BigDecimal.ZERO);
                log.debug("CMS: loan funded loanId={}", loanId);
            });
        });
    }

    // ── Helpers ───────────────────────────────────────────────────

    @FunctionalInterface
    private interface NodeConsumer { void accept(JsonNode node) throws Exception; }

    private void process(ConsumerRecord<String, String> rec, Acknowledgment ack,
                         String topic, NodeConsumer handler) {
        try {
            handler.accept(mapper.readTree(rec.value()));
            ack.acknowledge();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Malformed {} — skipping key={}", topic, rec.key(), e);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing {} key={}: {}", topic, rec.key(), e.getMessage(), e);
        }
    }

    private void incrementDailyStat(LocalDate date, long users, long loans, long funded, BigDecimal volume) {
        DailyStat stat = statRepo.findByStatDate(date)
                .orElse(DailyStat.builder().statDate(date).build());
        stat.setNewUsers(stat.getNewUsers() + users);
        stat.setNewLoans(stat.getNewLoans() + loans);
        stat.setFundedLoans(stat.getFundedLoans() + funded);
        stat.setLoanVolume(stat.getLoanVolume().add(volume));
        statRepo.save(stat);
    }
}
