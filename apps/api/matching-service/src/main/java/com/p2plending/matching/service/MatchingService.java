package com.p2plending.matching.service;

import com.p2plending.matching.config.MatchingProperties;
import com.p2plending.matching.domain.entity.InvestorPreference;
import com.p2plending.matching.domain.entity.MatchRecord;
import com.p2plending.matching.domain.entity.PendingLoan;
import com.p2plending.matching.domain.enums.MatchStatus;
import com.p2plending.matching.domain.repository.InvestorPreferenceRepository;
import com.p2plending.matching.domain.repository.MatchRecordRepository;
import com.p2plending.matching.domain.repository.PendingLoanRepository;
import com.p2plending.matching.dto.request.InvestorPreferenceRequest;
import com.p2plending.matching.dto.response.MatchRecordResponse;
import com.p2plending.matching.exception.ResourceNotFoundException;
import com.p2plending.matching.kafka.KafkaProducerService;
import com.p2plending.matching.kafka.event.LoanCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {

    // Scoring weights — must sum to 1.0
    private static final double W_AMOUNT   = 0.30;
    private static final double W_RATE     = 0.40;
    private static final double W_TERM     = 0.30;

    // Soft-match tolerance
    private static final double AMOUNT_BUFFER_PCT = 0.20;  // 20% over max still scores partially
    private static final int    TERM_BUFFER_MONTHS = 3;

    private final InvestorPreferenceRepository preferenceRepository;
    private final MatchRecordRepository        matchRecordRepository;
    private final PendingLoanRepository        pendingLoanRepository;
    private final KafkaProducerService         kafkaProducerService;
    private final MatchingProperties           props;

    // ── Trigger from Kafka loan.created ──────────────────────────

    @Transactional
    public void onLoanCreated(LoanCreatedEvent event) {
        // Persist snapshot for re-matching scheduler
        PendingLoan pending = PendingLoan.builder()
                .loanId(event.getLoanId())
                .borrowerId(event.getBorrowerId())
                .amount(event.getAmount())
                .interestRate(event.getInterestRate())
                .termMonths(event.getTermMonths())
                .purpose(event.getPurpose())
                .build();
        pendingLoanRepository.save(pending);

        int matched = runMatching(pending);
        log.info("loan.created loanId={} — {} matches found", event.getLoanId(), matched);
    }

    // ── Trigger from Kafka loan.funded ────────────────────────────

    @Transactional
    public void onLoanFunded(String loanId) {
        pendingLoanRepository.findById(loanId).ifPresent(loan -> {
            loan.setFullyFunded(true);
            pendingLoanRepository.save(loan);
        });
        int expired = matchRecordRepository.expireByLoanId(loanId);
        log.info("loan.funded loanId={} — {} pending matches expired", loanId, expired);
    }

    // ── Scheduled re-matching ────────────────────────────────────

    @Transactional
    public void reMatchUnfundedLoans() {
        List<PendingLoan> unfunded = pendingLoanRepository.findUnfundedOrderByLastMatched();
        log.info("Re-matching scheduler: {} unfunded loans to process", unfunded.size());

        int totalNew = 0;
        for (PendingLoan loan : unfunded) {
            totalNew += runMatching(loan);
            loan.setLastMatchedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
            pendingLoanRepository.save(loan);
        }
        log.info("Re-matching completed: {} new match records created", totalNew);
    }

    // ── Investor preference management ───────────────────────────

    @Transactional
    public void upsertPreference(String investorId, InvestorPreferenceRequest request) {
        InvestorPreference pref = preferenceRepository
                .findByInvestorIdAndActiveTrue(investorId)
                .orElse(InvestorPreference.builder().investorId(investorId).build());

        pref.setMinInvestmentAmount(request.getMinInvestmentAmount());
        pref.setMaxInvestmentAmount(request.getMaxInvestmentAmount());
        pref.setMinInterestRate(request.getMinInterestRate());
        pref.setMaxInterestRate(request.getMaxInterestRate());
        pref.setMinTermMonths(request.getMinTermMonths());
        pref.setMaxTermMonths(request.getMaxTermMonths());
        pref.setActive(true);

        preferenceRepository.save(pref);
        log.info("Preference upserted for investor={}", investorId);
    }

    // ── Query ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MatchRecordResponse> getMatchesForLoan(String loanId) {
        if (!pendingLoanRepository.existsById(loanId)) {
            throw new ResourceNotFoundException("No matching data found for loan: " + loanId);
        }
        return matchRecordRepository.findByLoanIdOrderByScoreDesc(loanId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Core matching algorithm ───────────────────────────────────

    /**
     * Scores all active investor preferences against a loan and creates MatchRecords
     * for candidates above the minimum threshold.
     *
     * @return number of new records created
     */
    private int runMatching(PendingLoan loan) {
        BigDecimal amountFloor = loan.getAmount()
                .multiply(BigDecimal.valueOf(1.0 - AMOUNT_BUFFER_PCT));

        List<InvestorPreference> candidates = preferenceRepository.findSoftMatches(
                loan.getAmount(),
                amountFloor,
                loan.getInterestRate(),
                loan.getTermMonths() - TERM_BUFFER_MONTHS,
                loan.getTermMonths() + TERM_BUFFER_MONTHS
        );

        // Score, filter, sort, cap
        List<ScoredCandidate> ranked = candidates.stream()
                .filter(p -> !matchRecordRepository.existsByLoanIdAndInvestorId(loan.getLoanId(), p.getInvestorId()))
                .map(p -> new ScoredCandidate(p.getInvestorId(), score(p, loan)))
                .filter(c -> c.score() >= props.getMinScoreThreshold())
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed())
                .limit(props.getMaxMatchesPerLoan())
                .toList();

        List<MatchRecord> records = new ArrayList<>();
        for (ScoredCandidate c : ranked) {
            MatchRecord record = MatchRecord.builder()
                    .loanId(loan.getLoanId())
                    .investorId(c.investorId())
                    .score(BigDecimal.valueOf(c.score()).setScale(3, RoundingMode.HALF_UP))
                    .status(MatchStatus.PENDING)
                    .build();
            records.add(record);
        }

        List<MatchRecord> saved = matchRecordRepository.saveAll(records);

        // Publish match.found events and mark NOTIFIED
        saved.forEach(r -> {
            kafkaProducerService.publishMatchFound(r, loan);
            r.setStatus(MatchStatus.NOTIFIED);
        });
        matchRecordRepository.saveAll(saved);

        return saved.size();
    }

    /**
     * Weighted scoring function. Returns a value in [0.0, 1.0].
     *
     * <pre>
     * Criteria           Weight   Full score if…
     * ──────────────────────────────────────────────
     * Amount range         30%    loan amount within [min, max]
     * Interest rate        40%    rate ≥ investor min (40%), or > max (28%)
     * Term preference      30%    term within [min, max]
     * </pre>
     * Partial scores apply for near-miss cases (see constants above).
     */
    private double score(InvestorPreference pref, PendingLoan loan) {
        double score = 0.0;

        // ── Amount (30%) ─────────────────────────────────────────
        int amtCmp = loan.getAmount().compareTo(pref.getMaxInvestmentAmount());
        if (loan.getAmount().compareTo(pref.getMinInvestmentAmount()) >= 0 && amtCmp <= 0) {
            score += W_AMOUNT;
        } else if (amtCmp > 0) {
            // Partially above max — partial credit if within 20% buffer
            BigDecimal excess = loan.getAmount().subtract(pref.getMaxInvestmentAmount());
            BigDecimal maxBuffer = pref.getMaxInvestmentAmount().multiply(BigDecimal.valueOf(AMOUNT_BUFFER_PCT));
            if (excess.compareTo(maxBuffer) <= 0) {
                score += W_AMOUNT * 0.5;
            }
        }

        // ── Interest rate (40%) ──────────────────────────────────
        if (loan.getInterestRate().compareTo(pref.getMinInterestRate()) >= 0) {
            if (pref.getMaxInterestRate() == null
                    || loan.getInterestRate().compareTo(pref.getMaxInterestRate()) <= 0) {
                score += W_RATE;        // within preferred range
            } else {
                score += W_RATE * 0.7;  // above preferred max, still acceptable
            }
        }

        // ── Term (30%) ───────────────────────────────────────────
        if (loan.getTermMonths() >= pref.getMinTermMonths()
                && loan.getTermMonths() <= pref.getMaxTermMonths()) {
            score += W_TERM;
        } else {
            int delta = Math.min(
                    Math.abs(loan.getTermMonths() - pref.getMinTermMonths()),
                    Math.abs(loan.getTermMonths() - pref.getMaxTermMonths())
            );
            if (delta <= TERM_BUFFER_MONTHS) {
                score += W_TERM * 0.5;
            }
        }

        return Math.min(1.0, score);
    }

    private MatchRecordResponse toResponse(MatchRecord r) {
        return MatchRecordResponse.builder()
                .id(r.getId())
                .loanId(r.getLoanId())
                .investorId(r.getInvestorId())
                .score(r.getScore())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private record ScoredCandidate(String investorId, double score) {}
}
