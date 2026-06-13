package com.p2plending.loan.service;

import com.p2plending.loan.client.AuthServiceClient;
import com.p2plending.loan.config.CacheConfig;
import com.p2plending.loan.domain.entity.LoanContract;
import com.p2plending.loan.domain.entity.LoanDocument;
import com.p2plending.loan.domain.entity.LoanOffer;
import com.p2plending.loan.domain.entity.LoanProduct;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.OfferStatus;
import com.p2plending.loan.domain.repository.LoanDocumentRepository;
import com.p2plending.loan.domain.repository.LoanOfferRepository;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.dto.request.LoanCreateRequest;
import com.p2plending.loan.dto.request.LoanDocumentInput;
import com.p2plending.loan.dto.request.LoanFilterParams;
import com.p2plending.loan.dto.request.LoanOfferCreateRequest;
import com.p2plending.loan.dto.response.LoanDocumentResponse;
import com.p2plending.loan.dto.response.LoanOfferResponse;
import com.p2plending.loan.dto.response.LoanResponse;
import com.p2plending.loan.dto.response.OfferCreateResponse;
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
            LoanStatus.AWAITING_DISBURSEMENT,
            LoanStatus.DISBURSED,
            LoanStatus.REPAYING
    );

    private final LoanRequestRepository  loanRequestRepository;
    private final LoanOfferRepository    loanOfferRepository;
    private final LoanDocumentRepository loanDocumentRepository;
    private final LoanRequestMapper      loanRequestMapper;
    private final LoanOfferMapper        loanOfferMapper;
    private final KafkaProducerService   kafkaProducerService;
    private final LoanProductService     loanProductService;
    private final RepaymentService       repaymentService;
    private final ContractService        contractService;
    private final AuthServiceClient      authServiceClient;

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

        // Lưu chứng từ đính kèm (tùy chọn)
        if (request.getDocuments() != null && !request.getDocuments().isEmpty()) {
            saveDocuments(saved.getId(), request.getDocuments());
        }

        kafkaProducerService.publishLoanSubmitted(saved);

        log.info("Loan submitted: id={} code={} borrower={} amount={} docs={}",
                saved.getId(), saved.getLoanCode(), borrowerId, saved.getAmount(),
                request.getDocuments() != null ? request.getDocuments().size() : 0);
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
        LoanResponse response = buildResponse(loan, true);
        authServiceClient.getUserById(loan.getBorrowerId()).ifPresent(user -> {
            response.setBorrowerFullName(user.getFullName());
            response.setBorrowerPhone(user.getPhone());
            response.setBorrowerCccd(user.getCccdNumber());
        });
        return response;
    }

    // ── Offer ─────────────────────────────────────────────────────

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_LOANS,      allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_LOAN_BY_ID, key = "#loanId")
    })
    public OfferCreateResponse createOffer(String loanId, LoanOfferCreateRequest request, String investorId) {
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

        // Offer ở trạng thái PENDING (giữ chỗ) — chỉ tính vào fundedAmount sau khi nhà đầu tư
        // ký hợp đồng đầu tư bằng OTP (ContractService.signContract).
        LoanOffer offer = LoanOffer.builder()
                .loanRequestId(loanId)
                .investorId(investorId)
                .amount(request.getAmount())
                .status(OfferStatus.PENDING)
                .build();

        LoanOffer saved = loanOfferRepository.save(offer);

        // Phát hành hợp đồng đầu tư PENDING_SIGNATURE để nhà đầu tư ký OTP.
        LoanContract contract = contractService.issueInvestmentContract(loan, saved);

        log.info("Offer created (PENDING, awaiting signature): id={} loan={} investor={} amount={} contract={}",
                saved.getId(), loanId, investorId, request.getAmount(), contract.getId());
        return OfferCreateResponse.builder()
                .offerId(saved.getId())
                .contract(contractService.toContractResponse(contract, loan))
                .build();
    }

    /** Giải ngân (OPS trên CMS): AWAITING_DISBURSEMENT → DISBURSED, sinh lịch trả nợ từ ngày giải ngân. */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_LOANS,      allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_LOAN_BY_ID, key = "#loanId")
    })
    public LoanResponse disburse(String loanId, String disbursedBy) {
        LoanRequest loan = findLoanOrThrow(loanId);

        if (loan.getStatus() != LoanStatus.AWAITING_DISBURSEMENT) {
            throw new InvalidLoanStateException(
                    "Khoản gọi vốn %s không ở trạng thái chờ giải ngân (status: %s)"
                    .formatted(loan.getLoanCode(), loan.getStatus()));
        }

        loan.setStatus(LoanStatus.DISBURSED);
        loan.setDisbursedAt(LocalDateTime.now());
        loan.setDisbursedBy(disbursedBy);
        loanRequestRepository.save(loan);

        // Sinh lịch trả nợ từ ngày giải ngân (generator dùng LocalDate.now).
        repaymentService.generateSchedule(loan);

        List<String> investorIds = loanOfferRepository
                .findByLoanRequestIdAndStatus(loanId, OfferStatus.ACCEPTED).stream()
                .map(LoanOffer::getInvestorId)
                .distinct()
                .toList();
        kafkaProducerService.publishLoanDisbursed(loan, investorIds);

        log.info("Loan {} disbursed by {} — schedule generated, loan.disbursed published", loanId, disbursedBy);
        return buildResponse(loan, false);
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
            // Ban lãnh đạo duyệt được từ khoản đang chờ thẩm định (duyệt thẳng) hoặc đã có đề xuất.
            Set<LoanStatus> reviewable = EnumSet.of(LoanStatus.PENDING_REVIEW, LoanStatus.PENDING_APPROVAL);
            if (!reviewable.contains(loan.getStatus())) {
                log.warn("loan.reviewed received but loan {} is not reviewable (status={})",
                        event.getLoanId(), loan.getStatus());
                return;
            }
            loan.setReviewedAt(event.getReviewedAt() != null ? event.getReviewedAt() : LocalDateTime.now());
            loan.setReviewedBy(event.getReviewedBy());

            if ("APPROVE".equalsIgnoreCase(event.getAction())) {
                // Số tiền cuối: ưu tiên số thẩm định viên đã đề xuất (nếu có).
                if (loan.getProposedAmount() != null) {
                    loan.setAmount(loan.getProposedAmount());
                }
                // Lãi suất cuối: ưu tiên lãi ban lãnh đạo nhập khi duyệt, fallback lãi đề xuất.
                BigDecimal finalRate = event.getInterestRate() != null
                        ? event.getInterestRate()
                        : loan.getProposedInterestRate();
                loan.setInterestRate(finalRate);
                loan.setStatus(LoanStatus.AWAITING_BORROWER_APPROVAL);
                loanRequestRepository.save(loan);
                kafkaProducerService.publishLoanApprovedAwaitingBorrower(loan);
                log.info("Loan {} approved by {} — awaiting borrower confirmation (final amount={}, rate={}%)",
                        event.getLoanId(), event.getReviewedBy(), loan.getAmount(), finalRate);
            } else {
                loan.setRejectionReason(event.getRejectionReason());
                loan.setStatus(LoanStatus.REJECTED);
                loanRequestRepository.save(loan);
                log.info("Loan {} rejected by {}: {}", event.getLoanId(), event.getReviewedBy(), event.getRejectionReason());
            }
        }, () -> log.warn("loan.reviewed received for unknown loan={}", event.getLoanId()));
    }

    /**
     * Cấp 1 — Thẩm định viên đề xuất số tiền & lãi suất trình ban lãnh đạo.
     * PENDING_REVIEW → PENDING_APPROVAL.
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_LOANS,      allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_LOAN_BY_ID, key = "#loanId")
    })
    public LoanResponse proposeLoan(String loanId, BigDecimal proposedAmount, BigDecimal proposedInterestRate,
                                    String note, String proposedBy) {
        LoanRequest loan = findLoanOrThrow(loanId);

        if (loan.getStatus() != LoanStatus.PENDING_REVIEW) {
            throw new InvalidLoanStateException(
                    "Khoản gọi vốn %s không ở trạng thái chờ thẩm định (status: %s)"
                    .formatted(loan.getLoanCode(), loan.getStatus()));
        }
        if (proposedAmount == null || proposedAmount.signum() <= 0) {
            throw new InvalidLoanStateException("Số tiền đề xuất phải lớn hơn 0");
        }
        if (proposedInterestRate == null || proposedInterestRate.signum() <= 0) {
            throw new InvalidLoanStateException("Lãi suất đề xuất phải lớn hơn 0");
        }

        loan.setProposedAmount(proposedAmount);
        loan.setProposedInterestRate(proposedInterestRate);
        loan.setProposedBy(proposedBy);
        loan.setProposedAt(LocalDateTime.now());
        loan.setAppraisalNote(note);
        loan.setStatus(LoanStatus.PENDING_APPROVAL);
        loanRequestRepository.save(loan);

        log.info("Loan {} proposed by {} — amount={}, rate={}% → awaiting leadership approval",
                loanId, proposedBy, proposedAmount, proposedInterestRate);
        return buildResponse(loan, false);
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

    // ── Documents ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LoanDocumentResponse> getDocuments(String loanId) {
        findLoanOrThrow(loanId);
        return loanDocumentRepository
                .findByLoanRequestIdAndIsDeletedFalseOrderByCreatedAtAsc(loanId)
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Transactional
    public List<LoanDocumentResponse> addDocuments(String loanId, String borrowerId,
                                                   List<LoanDocumentInput> inputs) {
        LoanRequest loan = findLoanOrThrow(loanId);
        if (!loan.getBorrowerId().equals(borrowerId)) {
            throw new InvalidLoanStateException("Bạn không có quyền thêm chứng từ vào khoản gọi vốn này");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất một chứng từ");
        }
        saveDocuments(loanId, inputs);
        return getDocuments(loanId);
    }

    private void saveDocuments(String loanId, List<LoanDocumentInput> inputs) {
        List<LoanDocument> docs = inputs.stream()
                .map(input -> LoanDocument.builder()
                        .loanRequestId(loanId)
                        .docType(input.getDocType())
                        .fileId(input.getFileId())
                        .fileName(input.getFileName())
                        .build())
                .toList();
        loanDocumentRepository.saveAll(docs);
    }

    private LoanDocumentResponse toDocumentResponse(LoanDocument doc) {
        return LoanDocumentResponse.builder()
                .id(doc.getId())
                .docType(doc.getDocType())
                .fileId(doc.getFileId())
                .fileName(doc.getFileName())
                .createdAt(doc.getCreatedAt())
                .build();
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

            List<LoanDocumentResponse> docs = loanDocumentRepository
                    .findByLoanRequestIdAndIsDeletedFalseOrderByCreatedAtAsc(loan.getId())
                    .stream()
                    .map(this::toDocumentResponse)
                    .toList();
            response.setDocuments(docs.isEmpty() ? null : docs);
        }
        return response;
    }

    // ─── Internal stats ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public com.p2plending.loan.dto.response.InternalLoanStatsResponse getLoanStats(java.time.LocalDate from) {
        java.time.LocalDateTime fromDt = from.atStartOfDay();
        java.time.ZoneId tz = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        java.time.LocalDateTime todayStart = java.time.LocalDate.now(tz).atStartOfDay();
        java.time.LocalDateTime tomorrowStart = todayStart.plusDays(1);

        long total   = loanRequestRepository.countAllActive();
        long pending = loanRequestRepository.countByStatusIn(
                java.util.List.of(LoanStatus.PENDING_REVIEW, LoanStatus.AWAITING_BORROWER_APPROVAL));
        long active  = loanRequestRepository.countByStatusIn(
                java.util.List.of(LoanStatus.ACTIVE, LoanStatus.FUNDED, LoanStatus.REPAYING));
        long funded  = loanRequestRepository.countByStatusIn(
                java.util.List.of(LoanStatus.FUNDED, LoanStatus.REPAYING, LoanStatus.COMPLETED));
        java.math.BigDecimal totalVol = loanRequestRepository.sumAmountByStatusIn(
                java.util.List.of(LoanStatus.FUNDED, LoanStatus.REPAYING, LoanStatus.COMPLETED));
        long todayCount = loanRequestRepository.countCreatedBetween(todayStart, tomorrowStart);
        java.math.BigDecimal todayVol = loanRequestRepository.sumAmountCreatedBetween(todayStart, tomorrowStart);

        java.util.List<Object[]> rows = loanRequestRepository.countDailyNewLoans(fromDt);
        java.util.List<com.p2plending.loan.dto.response.InternalLoanStatsResponse.DailyCount> daily = new java.util.ArrayList<>();
        for (Object[] row : rows) {
            java.time.LocalDate date = java.time.LocalDate.parse(row[0].toString());
            long count = ((Number) row[1]).longValue();
            java.math.BigDecimal vol = new java.math.BigDecimal(row[2].toString());
            daily.add(new com.p2plending.loan.dto.response.InternalLoanStatsResponse.DailyCount(date, count, vol));
        }

        return com.p2plending.loan.dto.response.InternalLoanStatsResponse.builder()
                .totalLoans(total)
                .pendingLoans(pending)
                .activeLoans(active)
                .fundedLoans(funded)
                .totalFundedVolume(totalVol != null ? totalVol : java.math.BigDecimal.ZERO)
                .newLoansToday(todayCount)
                .todayLoanVolume(todayVol != null ? todayVol : java.math.BigDecimal.ZERO)
                .dailyCounts(daily)
                .build();
    }
}
