package com.p2plending.loan.service;

import com.p2plending.loan.config.CacheConfig;
import com.p2plending.loan.domain.entity.LoanOffer;
import com.p2plending.loan.domain.entity.LoanProduct;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.OfferStatus;
import com.p2plending.loan.domain.repository.LoanOfferRepository;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.dto.request.LoanCreateRequest;
import com.p2plending.loan.dto.request.LoanFilterParams;
import com.p2plending.loan.dto.request.LoanOfferCreateRequest;
import com.p2plending.loan.dto.response.LoanOfferResponse;
import com.p2plending.loan.dto.response.LoanResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import com.p2plending.loan.exception.InvalidLoanStateException;
import com.p2plending.loan.exception.LoanNotFoundException;
import com.p2plending.loan.kafka.KafkaProducerService;
import com.p2plending.loan.kafka.event.LoanReviewedEvent;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {

    private static final Set<LoanStatus> OFFERABLE_STATUSES = EnumSet.of(LoanStatus.ACTIVE);
    private static final BigDecimal MIN_OFFER   = new BigDecimal("500000");
    private static final BigDecimal OFFER_UNIT  = new BigDecimal("500000");

    /** Trạng thái "đang hoạt động" — borrower chưa thể tạo khoản vay mới khi còn khoản ở các trạng thái này. */
    private static final Set<LoanStatus> BLOCKING_STATUSES = EnumSet.of(
            LoanStatus.PENDING_REVIEW,
            LoanStatus.AWAITING_BORROWER_APPROVAL,
            LoanStatus.ACTIVE,
            LoanStatus.FUNDED,
            LoanStatus.REPAYING
    );

    private final LoanRequestRepository loanRequestRepository;
    private final LoanOfferRepository   loanOfferRepository;
    private final LoanRequestMapper     loanRequestMapper;
    private final LoanOfferMapper       loanOfferMapper;
    private final KafkaProducerService  kafkaProducerService;
    private final LoanProductService    loanProductService;

    // ── Create ────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_LOANS, allEntries = true)
    public LoanResponse createLoan(LoanCreateRequest request, String borrowerId) {
        // 1. Validate sản phẩm gọi vốn
        LoanProduct product = loanProductService.findByCodeOrThrow(request.getProductCode().toUpperCase());

        if (!product.isActive()) {
            throw new InvalidLoanStateException(
                    "Sản phẩm gọi vốn '%s' hiện không còn hoạt động".formatted(product.getName()));
        }

        // 2. Validate số tiền theo giới hạn sản phẩm
        if (!product.isAmountInRange(request.getAmount())) {
            throw new InvalidLoanStateException(
                    "Số tiền gọi vốn cho sản phẩm '%s' phải từ %,.0f đến %,.0f VNĐ"
                    .formatted(product.getName(),
                               product.getMinAmount().doubleValue(),
                               product.getMaxAmount().doubleValue()));
        }

        // 3. Validate kỳ hạn theo danh sách cho phép
        if (!product.isTermAllowed(request.getTermMonths())) {
            throw new InvalidLoanStateException(
                    "Kỳ hạn %d tháng không hợp lệ cho sản phẩm '%s'. Kỳ hạn cho phép: %s"
                    .formatted(request.getTermMonths(), product.getName(), product.getAvailableTerms()));
        }

        // 4. Kiểm tra borrower không có khoản vay đang hoạt động
        if (loanRequestRepository.existsByBorrowerIdAndStatusInAndIsDeletedFalse(borrowerId, BLOCKING_STATUSES)) {
            throw new InvalidLoanStateException(
                    "Bạn đang có một khoản gọi vốn chưa hoàn tất. " +
                    "Vui lòng chờ khoản hiện tại được hủy hoặc hoàn tất trước khi tạo khoản mới.");
        }

        LoanRequest loan = loanRequestMapper.toEntity(request);
        loan.setProductId(product.getId());
        loan.setBorrowerId(borrowerId);
        loan.setStatus(LoanStatus.PENDING_REVIEW);

        // saveAndFlush then re-fetch so MySQL assigns loan_seq before we use getLoanCode()
        loanRequestRepository.saveAndFlush(loan);
        LoanRequest saved = findLoanOrThrow(loan.getId());
        kafkaProducerService.publishLoanSubmitted(saved);

        log.info("Loan submitted: id={} code={} borrower={} amount={}",
                saved.getId(), saved.getLoanCode(), borrowerId, saved.getAmount());
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
                    "Khoản gọi vốn %s không trong trạng thái nhận đầu tư (status: %s)"
                    .formatted(loan.getLoanCode(), loan.getStatus()));
        }
        if (investorId.equals(loan.getBorrowerId())) {
            throw new InvalidLoanStateException("Người gọi vốn không thể tự đầu tư vào khoản của mình");
        }

        BigDecimal remaining = loan.getRemainingAmount();

        // Must be a multiple of 500,000 unless it exactly fills the remaining amount
        if (request.getAmount().remainder(OFFER_UNIT).compareTo(BigDecimal.ZERO) != 0
                && request.getAmount().compareTo(remaining) != 0) {
            throw new InvalidLoanStateException(
                    "Số tiền đầu tư phải là bội số của 500,000 VNĐ (hoặc đầu tư toàn bộ %,.0f VNĐ còn lại)"
                    .formatted(remaining));
        }

        // Cannot exceed remaining
        if (request.getAmount().compareTo(remaining) > 0) {
            throw new InvalidLoanStateException(
                    "Số tiền đầu tư vượt quá số tiền còn lại: %,.0f VNĐ".formatted(remaining));
        }

        // After this offer, remaining must be 0 or ≥ 500,000 to keep the loan investable
        BigDecimal remainingAfter = remaining.subtract(request.getAmount());
        if (remainingAfter.compareTo(BigDecimal.ZERO) > 0 && remainingAfter.compareTo(MIN_OFFER) < 0) {
            BigDecimal maxAllowed = remaining.subtract(MIN_OFFER)
                    .divideToIntegralValue(OFFER_UNIT).multiply(OFFER_UNIT);
            throw new InvalidLoanStateException(
                    "Số tiền còn lại sau đầu tư (%,.0f VNĐ) sẽ dưới mức tối thiểu 500,000 VNĐ. "
                    .formatted(remainingAfter)
                    + (maxAllowed.compareTo(BigDecimal.ZERO) > 0
                        ? "Tối đa có thể đầu tư %,.0f VNĐ, hoặc đầu tư toàn bộ %,.0f VNĐ còn lại."
                          .formatted(maxAllowed, remaining)
                        : "Hãy đầu tư toàn bộ %,.0f VNĐ còn lại.".formatted(remaining)));
        }

        LoanOffer offer = LoanOffer.builder()
                .loanRequestId(loanId)
                .investorId(investorId)
                .amount(request.getAmount())
                .status(OfferStatus.ACCEPTED)
                .build();

        LoanOffer saved = loanOfferRepository.save(offer);

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

    // ── Kafka consumer callbacks ──────────────────────────────────

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_LOANS,      allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_LOAN_BY_ID, key = "#event.loanId")
    })
    public void handleLoanReviewed(LoanReviewedEvent event) {
        applyLoanReview(event);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_LOANS,      allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_LOAN_BY_ID, key = "#loanId")
    })
    public LoanResponse reviewLoan(String loanId, String action, BigDecimal interestRate, String rejectionReason, String reviewedBy) {
        LoanReviewedEvent event = LoanReviewedEvent.builder()
                .loanId(loanId)
                .action(action)
                .interestRate(interestRate)
                .rejectionReason(rejectionReason)
                .reviewedBy(reviewedBy)
                .reviewedAt(LocalDateTime.now())
                .build();
        applyLoanReview(event);
        return getLoanById(loanId);
    }

    private void applyLoanReview(LoanReviewedEvent event) {
        loanRequestRepository.findById(event.getLoanId()).ifPresentOrElse(loan -> {
            if (loan.getStatus() != LoanStatus.PENDING_REVIEW) {
                log.warn("loan.reviewed received but loan {} is not PENDING_REVIEW (status={})",
                        event.getLoanId(), loan.getStatus());
                return;
            }
            loan.setReviewedAt(event.getReviewedAt() != null ? event.getReviewedAt() : LocalDateTime.now());

            if ("APPROVE".equalsIgnoreCase(event.getAction())) {
                loan.setInterestRate(event.getInterestRate());
                loan.setStatus(LoanStatus.AWAITING_BORROWER_APPROVAL);
                loanRequestRepository.save(loan);
                log.info("Loan {} approved by {} — awaiting borrower confirmation (proposed rate={}%)",
                        event.getLoanId(), event.getReviewedBy(), event.getInterestRate());
            } else {
                loan.setRejectionReason(event.getRejectionReason());
                loan.setStatus(LoanStatus.REJECTED);
                loanRequestRepository.save(loan);
                log.info("Loan {} rejected by {}: {}", event.getLoanId(), event.getReviewedBy(), event.getRejectionReason());
            }
        }, () -> log.warn("loan.reviewed received for unknown loan={}", event.getLoanId()));
    }

    // ── Borrower confirmation ─────────────────────────────────────

    /**
     * Borrower accepts the proposed terms from CMS.
     * Transitions AWAITING_BORROWER_APPROVAL → ACTIVE and publishes loan.created
     * to trigger matching-service.
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_LOANS,      allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_LOAN_BY_ID, key = "#loanId")
    })
    public LoanResponse confirmLoan(String loanId, String borrowerId) {
        LoanRequest loan = findLoanOrThrow(loanId);

        if (!loan.getBorrowerId().equals(borrowerId)) {
            throw new InvalidLoanStateException("Bạn không có quyền xác nhận khoản gọi vốn này");
        }
        if (loan.getStatus() != LoanStatus.AWAITING_BORROWER_APPROVAL) {
            throw new InvalidLoanStateException(
                    "Khoản gọi vốn %s không ở trạng thái chờ xác nhận (status: %s)"
                    .formatted(loan.getLoanCode(), loan.getStatus()));
        }

        loan.setStatus(LoanStatus.ACTIVE);
        loanRequestRepository.save(loan);
        kafkaProducerService.publishLoanCreated(loan);

        log.info("Loan {} confirmed by borrower {} — now ACTIVE, triggering matching",
                loanId, borrowerId);
        return buildResponse(loan, false);
    }

    /**
     * Borrower cancels/declines the application.
     * Valid from PENDING_REVIEW or AWAITING_BORROWER_APPROVAL.
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_LOANS,      allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_LOAN_BY_ID, key = "#loanId")
    })
    public LoanResponse cancelLoan(String loanId, String borrowerId, String reason) {
        LoanRequest loan = findLoanOrThrow(loanId);

        if (!loan.getBorrowerId().equals(borrowerId)) {
            throw new InvalidLoanStateException("Bạn không có quyền hủy khoản gọi vốn này");
        }

        Set<LoanStatus> cancellableStatuses = EnumSet.of(
                LoanStatus.PENDING_REVIEW, LoanStatus.AWAITING_BORROWER_APPROVAL);
        if (!cancellableStatuses.contains(loan.getStatus())) {
            throw new InvalidLoanStateException(
                    "Không thể hủy khoản gọi vốn %s ở trạng thái hiện tại (%s)"
                    .formatted(loan.getLoanCode(), loan.getStatus()));
        }

        loan.setStatus(LoanStatus.CANCELLED);
        loan.setBorrowerCancelledReason(reason);
        loanRequestRepository.save(loan);

        log.info("Loan {} cancelled by borrower {}: {}", loanId, borrowerId, reason);
        return buildResponse(loan, false);
    }

    /**
     * Returns paginated list of loans belonging to the authenticated borrower.
     * All statuses are included so the borrower can track the full lifecycle.
     */
    @Transactional(readOnly = true)
    public PagedResponse<LoanResponse> getMyLoans(String borrowerId, int page, int size) {
        LoanFilterParams params = new LoanFilterParams();
        params.setBorrowerId(borrowerId);
        params.setPage(page);
        params.setSize(size);
        params.setSortBy("createdAt");
        params.setSortDir("desc");
        return getLoans(params);
    }

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

        // Enrich với thông tin sản phẩm nếu có
        if (loan.getProductId() != null) {
            loanProductService.findProductById(loan.getProductId()).ifPresent(product -> {
                response.setProductCode(product.getCode());
                response.setProductName(product.getName());
            });
        }

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
