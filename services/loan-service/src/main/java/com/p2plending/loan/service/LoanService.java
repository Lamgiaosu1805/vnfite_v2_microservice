package com.p2plending.loan.service;

import com.p2plending.loan.config.CacheConfig;
import com.p2plending.loan.domain.entity.LoanOffer;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.OfferStatus;
import com.p2plending.loan.domain.repository.LoanOfferRepository;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.dto.request.LoanCreateRequest;
import com.p2plending.loan.dto.request.LoanFilterParams;
import com.p2plending.loan.dto.request.LoanOfferCreateRequest;
import com.p2plending.loan.dto.request.LoanStatusUpdateRequest;
import com.p2plending.loan.dto.response.LoanOfferResponse;
import com.p2plending.loan.dto.response.LoanResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import com.p2plending.loan.exception.InvalidLoanStateException;
import com.p2plending.loan.exception.LoanNotFoundException;
import com.p2plending.loan.kafka.KafkaProducerService;
import com.p2plending.loan.kafka.event.PaymentCompletedEvent;
import com.p2plending.loan.mapper.LoanOfferMapper;
import com.p2plending.loan.mapper.LoanRequestMapper;
import com.p2plending.loan.specification.LoanSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {

    // Statuses in which a loan can receive new offers
    private static final Set<LoanStatus> OFFERABLE_STATUSES = EnumSet.of(LoanStatus.ACTIVE);

    // Statuses from which a manual status update is allowed
    private static final Set<LoanStatus> UPDATABLE_STATUSES =
            EnumSet.of(LoanStatus.PENDING, LoanStatus.ACTIVE, LoanStatus.FUNDED,
                       LoanStatus.REPAYING, LoanStatus.DEFAULTED);

    private final LoanRequestRepository loanRequestRepository;
    private final LoanOfferRepository   loanOfferRepository;
    private final LoanRequestMapper     loanRequestMapper;
    private final LoanOfferMapper       loanOfferMapper;
    private final KafkaProducerService  kafkaProducerService;

    // ── Create ────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_LOANS, allEntries = true)
    public LoanResponse createLoan(LoanCreateRequest request, String borrowerId) {
        LoanRequest loan = loanRequestMapper.toEntity(request);
        loan.setBorrowerId(borrowerId);
        loan.setStatus(LoanStatus.PENDING);

        LoanRequest saved = loanRequestRepository.save(loan);
        kafkaProducerService.publishLoanCreated(saved);

        log.info("Loan created: id={} borrower={} amount={}", saved.getId(), borrowerId, saved.getAmount());
        return buildResponse(saved, false);
    }

    // ── Read ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_LOANS, key = "#params.cacheKey()")
    public PagedResponse<LoanResponse> getLoans(LoanFilterParams params) {
        log.debug("Cache miss — querying loans with params: {}", params.cacheKey());
        Page<LoanResponse> page = loanRequestRepository
                .findAll(LoanSpecification.withFilters(params), params.toPageable())
                .map(loan -> buildResponse(loan, false));
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_LOAN_BY_ID, key = "#id")
    public LoanResponse getLoanById(String id) {
        LoanRequest loan = findLoanOrThrow(id);
        return buildResponse(loan, true);
    }

    // ── Offer ─────────────────────────────────────────────────────

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_LOANS,      allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_LOAN_BY_ID, key = "#loanId")
    })
    public LoanOfferResponse createOffer(String loanId, LoanOfferCreateRequest request, String investorId) {
        LoanRequest loan = findLoanOrThrow(loanId);

        if (!OFFERABLE_STATUSES.contains(loan.getStatus())) {
            throw new InvalidLoanStateException(
                    "Loan %s is not accepting offers — current status: %s".formatted(loanId, loan.getStatus()));
        }
        if (investorId.equals(loan.getBorrowerId())) {
            throw new InvalidLoanStateException("Borrower cannot invest in their own loan");
        }
        if (request.getAmount().compareTo(loan.getRemainingAmount()) > 0) {
            throw new InvalidLoanStateException(
                    "Offer amount %s exceeds remaining amount %s"
                    .formatted(request.getAmount(), loan.getRemainingAmount()));
        }

        LoanOffer offer = LoanOffer.builder()
                .loanRequestId(loanId)
                .investorId(investorId)
                .amount(request.getAmount())
                .status(OfferStatus.ACCEPTED)
                .build();

        LoanOffer saved = loanOfferRepository.save(offer);

        // Update funded amount and check for FUNDED transition
        loan.setFundedAmount(loan.getFundedAmount().add(request.getAmount()));
        if (loan.isFullyFunded()) {
            loan.setStatus(LoanStatus.FUNDED);
            loanRequestRepository.save(loan);
            kafkaProducerService.publishLoanFunded(loan);
            log.info("Loan {} fully funded — publishing loan.funded event", loanId);
        } else {
            loanRequestRepository.save(loan);
        }

        log.info("Offer created: id={} loan={} investor={} amount={}",
                saved.getId(), loanId, investorId, request.getAmount());
        return loanOfferMapper.toResponse(saved);
    }

    // ── Update status ─────────────────────────────────────────────

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_LOANS,      allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_LOAN_BY_ID, key = "#loanId")
    })
    public LoanResponse updateStatus(String loanId, LoanStatusUpdateRequest request) {
        LoanRequest loan = findLoanOrThrow(loanId);

        if (!UPDATABLE_STATUSES.contains(loan.getStatus())) {
            throw new InvalidLoanStateException(
                    "Cannot update loan %s — terminal status: %s".formatted(loanId, loan.getStatus()));
        }

        LoanStatus prev = loan.getStatus();
        loan.setStatus(request.getStatus());
        LoanRequest saved = loanRequestRepository.save(loan);

        log.info("Loan {} status updated: {} → {} reason={}",
                loanId, prev, request.getStatus(), request.getReason());
        return buildResponse(saved, false);
    }

    // ── Kafka consumer callback ───────────────────────────────────

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_LOANS,      allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_LOAN_BY_ID, key = "#event.loanId")
    })
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        loanRequestRepository.findById(event.getLoanId()).ifPresentOrElse(loan -> {
            if (loan.getStatus() == LoanStatus.FUNDED || loan.getStatus() == LoanStatus.REPAYING) {
                LoanStatus next = event.isFinalPayment() ? LoanStatus.COMPLETED : LoanStatus.REPAYING;
                loan.setStatus(next);
                loanRequestRepository.save(loan);
                log.info("Loan {} transitioned to {} after payment {} (final={})",
                        event.getLoanId(), next, event.getPaymentId(), event.isFinalPayment());
            }
        }, () -> log.warn("payment.completed received for unknown loan={}", event.getLoanId()));
    }

    // ── Internals ─────────────────────────────────────────────────

    private LoanRequest findLoanOrThrow(String id) {
        return loanRequestRepository.findById(id)
                .orElseThrow(() -> new LoanNotFoundException(id));
    }

    private LoanResponse buildResponse(LoanRequest loan, boolean includeOffers) {
        LoanResponse response = loanRequestMapper.toResponse(loan);
        if (includeOffers) {
            List<LoanOfferResponse> offers = loanOfferRepository
                    .findByLoanRequestId(loan.getId()).stream()
                    .map(loanOfferMapper::toResponse)
                    .toList();
            response.setOffers(offers);
        }
        return response;
    }
}
