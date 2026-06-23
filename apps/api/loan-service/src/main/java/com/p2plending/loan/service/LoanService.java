package com.p2plending.loan.service;

import com.p2plending.loan.client.AuthServiceClient;
import com.p2plending.loan.client.PaymentServiceClient;
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
import com.p2plending.loan.dto.response.LoanPublicResponse;
import com.p2plending.loan.dto.response.LoanResponse;
import com.p2plending.loan.dto.response.MarketplaceStatsResponse;
import com.p2plending.loan.dto.response.OfferCreateResponse;
import com.p2plending.loan.dto.response.PagedResponse;
import com.p2plending.loan.exception.InvalidLoanStateException;
import com.p2plending.loan.exception.LoanNotFoundException;
import com.p2plending.loan.kafka.KafkaProducerService;
import com.p2plending.loan.kafka.event.LoanReviewedEvent;
import com.p2plending.loan.kafka.event.PaymentCompletedEvent;
import com.p2plending.loan.mapper.LoanOfferMapper;
import com.p2plending.loan.mapper.LoanRequestMapper;
import com.p2plending.loan.security.AuthenticatedUser;
import com.p2plending.loan.specification.LoanSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private final PaymentServiceClient   paymentServiceClient;
    private final CacheManager           cacheManager;

    /** Số ngày một khoản được phép ở trạng thái ACTIVE để gọi vốn trước khi hết hạn & hoàn tiền. */
    @Value("${app.funding.window-days:30}")
    private int fundingWindowDays;

    /** Số ngày người gọi vốn được phép ký khế ước sau khi FUNDED; quá hạn → hủy & hoàn tiền. */
    @Value("${app.funding.signing-window-days:7}")
    private int signingWindowDays;

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
    @Cacheable(value = CacheConfig.CACHE_LOANS, key = "'public:' + #params.cacheKey()")
    public PagedResponse<LoanPublicResponse> getPublicLoans(LoanFilterParams params) {
        Page<LoanPublicResponse> page = loanRequestRepository
                .findAll(LoanSpecification.withFilters(params), params.toPageable())
                .map(this::buildPublicResponse);
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

    /**
     * Trả full detail (có PII) cho chủ sở hữu khoản gọi vốn và CMS.
     * Nhà đầu tư (authenticated nhưng không phải owner/CMS) nhận public view không có PII.
     */
    @Transactional(readOnly = true)
    public Object getLoanByIdForCaller(String id, AuthenticatedUser caller) {
        LoanRequest loan = findLoanOrThrow(id);
        if (caller != null && (loan.getBorrowerId().equals(caller.userId()) || hasCmsRole(caller))) {
            return getLoanById(id);
        }
        return buildPublicResponse(loan);
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

        // Kiểm tra số dư ví nhà đầu tư (server-side, không tin client). Tiền chỉ thực sự
        // bị khóa khi ký hợp đồng (ContractService.applyInvestmentSigned); đây là fail-fast
        // để không phát hành hợp đồng cho lệnh không đủ tiền.
        BigDecimal available = paymentServiceClient.getAvailableBalance(investorId);
        if (available.compareTo(request.getAmount()) < 0) {
            throw new InvalidLoanStateException(
                    "Số dư ví không đủ để đầu tư. Khả dụng: %,.0f VNĐ, cần: %,.0f VNĐ"
                    .formatted(available, request.getAmount()));
        }

        // Không cho phép cùng nhà đầu tư đặt offer trùng khi đã có offer đang PENDING hoặc ACCEPTED
        if (loanOfferRepository.existsByLoanRequestIdAndInvestorIdAndStatusIn(
                loanId, investorId, List.of(OfferStatus.PENDING, OfferStatus.ACCEPTED))) {
            throw new InvalidLoanStateException(
                    "Bạn đã có lệnh đầu tư đang chờ xử lý hoặc đã được chấp nhận cho khoản gọi vốn này.");
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
        loan.setDisbursedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        loan.setDisbursedBy(disbursedBy);
        loanRequestRepository.save(loan);

        // Sinh lịch trả nợ từ ngày giải ngân (generator dùng LocalDate.now).
        repaymentService.generateSchedule(loan);

        List<LoanOffer> acceptedOffers = loanOfferRepository
                .findByLoanRequestIdAndStatus(loanId, OfferStatus.ACCEPTED);

        // Tính tổng offer ACCEPTED — phải khớp với loan.amount trước khi debit bất kỳ nhà đầu tư nào.
        BigDecimal totalAccepted = acceptedOffers.stream()
                .map(LoanOffer::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAccepted.compareTo(loan.getAmount()) != 0) {
            throw new InvalidLoanStateException(
                    "Tổng offer ACCEPTED (%,.0f VNĐ) không khớp với số tiền khoản gọi vốn %s (%,.0f VNĐ) — dừng giải ngân"
                    .formatted(totalAccepted, loan.getLoanCode(), loan.getAmount()));
        }

        // Tính netAmount trước khi debit — không được âm hoặc bằng 0 với khoản mới.
        boolean isNewLoan = loan.getNetDisbursement() != null;
        BigDecimal netAmount;
        if (isNewLoan) {
            // Khoản mới: phí đã được tính và lưu snapshot tại bước propose.
            // Đối chiếu snapshot với kết quả thực tế (totalAccepted - totalFee).
            BigDecimal recalculated = totalAccepted.subtract(loan.getTotalFee());
            if (loan.getNetDisbursement().compareTo(recalculated) != 0) {
                throw new InvalidLoanStateException(
                        "Snapshot netDisbursement (%,.0f VNĐ) lệch với kết quả thực tế (%,.0f VNĐ) — dừng giải ngân khoản %s"
                        .formatted(loan.getNetDisbursement(), recalculated, loan.getLoanCode()));
            }
            netAmount = loan.getNetDisbursement();
            if (netAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidLoanStateException(
                        "Số tiền thực nhận sau phí không hợp lệ (%,.0f VNĐ) — dừng giải ngân khoản %s"
                        .formatted(netAmount, loan.getLoanCode()));
            }
        } else {
            // Khoản legacy (trước khi có tính năng phí): giải ngân toàn bộ, không thu phí hồi tố.
            netAmount = totalAccepted;
        }

        // Debit từng nhà đầu tư sau khi validation hoàn tất. referenceId theo offerId để
        // payment-service idempotent — retry giải ngân không debit trùng.
        BigDecimal totalDisbursed = BigDecimal.ZERO;
        for (LoanOffer offer : acceptedOffers) {
            paymentServiceClient.debit(offer.getInvestorId(), offer.getAmount(),
                    "Giải ngân khoản gọi vốn " + loan.getLoanCode(),
                    "DISBURSE-" + offer.getId());
            totalDisbursed = totalDisbursed.add(offer.getAmount());
        }
        loanRequestRepository.save(loan);

        // Credit tiền thực nhận (đã trừ phí) vào ví VNF của người gọi vốn.
        // Người gọi vốn sau đó tự rút về tài khoản ngân hàng qua luồng withdraw.
        String feeNote = isNewLoan
                ? " (đã trừ phí thẩm định %,.0f VNĐ)".formatted(loan.getTotalFee())
                : "";
        paymentServiceClient.creditBorrower(loan.getBorrowerId(), netAmount,
                "Nhận tiền giải ngân khoản vay " + loan.getLoanCode() + feeNote,
                "CREDIT-BORROWER-" + loanId);

        List<String> investorIds = acceptedOffers.stream()
                .map(LoanOffer::getInvestorId)
                .distinct()
                .toList();
        kafkaProducerService.publishLoanDisbursed(loan, investorIds);

        log.info("Loan {} disbursed by {} — schedule generated, loan.disbursed published", loanId, disbursedBy);
        return buildResponse(loan, false);
    }

    // ── Hết hạn gọi vốn / ký khế ước (scheduler) ──────────────────

    /** ID các khoản ACTIVE đã quá hạn gọi vốn — đọc riêng để xử lý từng khoản trong transaction độc lập. */
    @Transactional(readOnly = true)
    public List<String> findExpiredActiveLoanIds() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(fundingWindowDays);
        return loanRequestRepository
                .findByStatusAndActivatedAtBeforeAndIsDeletedFalse(LoanStatus.ACTIVE, cutoff)
                .stream().map(LoanRequest::getId).toList();
    }

    /** ID các khoản FUNDED kẹt — người gọi vốn không ký khế ước trong thời hạn cho phép. */
    @Transactional(readOnly = true)
    public List<String> findStuckFundedLoanIds() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(signingWindowDays);
        return loanRequestRepository
                .findByStatusAndFundedAtBeforeAndIsDeletedFalse(LoanStatus.FUNDED, cutoff)
                .stream().map(LoanRequest::getId).toList();
    }

    /**
     * Hết hạn một khoản (ACTIVE chưa đủ vốn, hoặc FUNDED chưa ký khế ước): hoàn tiền (unlock)
     * toàn bộ nhà đầu tư đã cam kết, void hợp đồng chưa ký, chuyển khoản sang CANCELLED.
     * Guard theo {@code expectedStatus} để bỏ qua khoản đã đổi trạng thái. Idempotent — chạy lại an toàn.
     */
    @Transactional
    public void expireAndRefund(String loanId, LoanStatus expectedStatus, String reason) {
        LoanRequest loan = findLoanOrThrow(loanId);
        if (loan.getStatus() != expectedStatus) {
            return; // trạng thái đã đổi từ lúc quét — bỏ qua
        }

        contractService.refundInvestorsAndVoid(loan, reason);

        loan.setStatus(LoanStatus.CANCELLED);
        loan.setBorrowerCancelledReason(reason);
        loanRequestRepository.save(loan);

        evictLoanCaches(loanId);
        log.info("Loan {} expired from {} — investors refunded, CANCELLED ({})",
                loanId, expectedStatus, reason);
    }

    private void evictLoanCaches(String loanId) {
        var byId = cacheManager.getCache(CacheConfig.CACHE_LOAN_BY_ID);
        if (byId != null) byId.evict(loanId);
        var list = cacheManager.getCache(CacheConfig.CACHE_LOANS);
        if (list != null) list.clear();
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
                .reviewedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                .build();
        applyLoanReview(event);
        return getLoanById(loanId);
    }

    private void applyLoanReview(LoanReviewedEvent event) {
        loanRequestRepository.findById(event.getLoanId()).ifPresentOrElse(loan -> {
            // REJECT được phép từ cả PENDING_REVIEW lẫn PENDING_APPROVAL.
            // APPROVE chỉ được phép từ PENDING_APPROVAL (đã có phí thẩm định từ thẩm định viên).
            Set<LoanStatus> rejectable = EnumSet.of(LoanStatus.PENDING_REVIEW, LoanStatus.PENDING_APPROVAL);
            boolean isApprove = "APPROVE".equalsIgnoreCase(event.getAction());

            if (isApprove && loan.getStatus() != LoanStatus.PENDING_APPROVAL) {
                throw new InvalidLoanStateException(
                        "Khoản gọi vốn %s chưa qua bước thẩm định — không thể phê duyệt trực tiếp từ trạng thái %s. "
                        + "Thẩm định viên phải trình đề xuất trước."
                        .formatted(event.getLoanId(), loan.getStatus()));
            }
            if (!isApprove && !rejectable.contains(loan.getStatus())) {
                log.warn("loan.reviewed received but loan {} is not rejectable (status={})",
                        event.getLoanId(), loan.getStatus());
                return;
            }

            loan.setReviewedAt(event.getReviewedAt() != null ? event.getReviewedAt() : LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
            loan.setReviewedBy(event.getReviewedBy());

            if (isApprove) {
                // Số tiền cuối = proposedAmount (thẩm định viên đã xác nhận, phí đã tính theo đó).
                loan.setAmount(loan.getProposedAmount());
                // Lãi suất cuối: ban lãnh đạo có thể override khi duyệt, fallback lãi đề xuất.
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
                                    BigDecimal appraisalFeeRate, String note, String proposedBy) {
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
        loan.setProposedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        loan.setAppraisalNote(note);
        loan.setStatus(LoanStatus.PENDING_APPROVAL);

        // Tính phí tại bước trình duyệt — snapshot theo điều khoản người gọi vốn sẽ xác nhận.
        // appraisalFeeRate đã được validate @NotNull ở DTO; 0.00 = chủ động miễn phí.
        BigDecimal rate = appraisalFeeRate;
        loan.setAppraisalFeeRate(rate);
        BigDecimal appraisalFee = proposedAmount.multiply(rate)
                .divide(new java.math.BigDecimal("100"), 0, java.math.RoundingMode.HALF_UP);
        BigDecimal vatAmount    = appraisalFee.multiply(new java.math.BigDecimal("0.10"))
                .setScale(0, java.math.RoundingMode.HALF_UP);
        BigDecimal totalFee     = appraisalFee.add(vatAmount);
        if (totalFee.compareTo(proposedAmount) >= 0) {
            throw new InvalidLoanStateException(
                    "Tổng phí thẩm định (%,.0f VNĐ) không được lớn hơn hoặc bằng số tiền đề xuất (%,.0f VNĐ)"
                    .formatted(totalFee, proposedAmount));
        }
        BigDecimal netDisbursement = proposedAmount.subtract(totalFee);
        loan.setAppraisalFee(appraisalFee);
        loan.setVatAmount(vatAmount);
        loan.setTotalFee(totalFee);
        loan.setNetDisbursement(netDisbursement);

        loanRequestRepository.save(loan);

        log.info("Loan {} proposed by {} — amount={}, rate={}%, feeRate={}% → awaiting leadership approval",
                loanId, proposedBy, proposedAmount, proposedInterestRate, rate);
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
        loan.setActivatedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
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

        // Phí được tính tại bước propose và lưu thẳng vào loan_requests.

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

    private LoanPublicResponse buildPublicResponse(LoanRequest loan) {
        LoanPublicResponse response = LoanPublicResponse.builder()
                .id(loan.getId())
                .loanCode(loan.getLoanCode())
                .productId(loan.getProductId())
                .amount(loan.getAmount())
                .interestRate(loan.getInterestRate())
                .proposedAmount(loan.getProposedAmount())
                .proposedInterestRate(loan.getProposedInterestRate())
                .termMonths(loan.getTermMonths())
                .purpose(loan.getPurpose())
                .occupation(loan.getOccupation())
                .province(loan.getProvince())
                .status(loan.getStatus())
                .fundedAmount(loan.getFundedAmount())
                .remainingAmount(loan.getRemainingAmount())
                .appraisalFee(loan.getAppraisalFee())
                .vatAmount(loan.getVatAmount())
                .totalFee(loan.getTotalFee())
                .netDisbursement(loan.getNetDisbursement())
                .createdAt(loan.getCreatedAt())
                .updatedAt(loan.getUpdatedAt())
                // Người tham chiếu — không chứa PII nhạy cảm, phone được che
                .ref1FullName(loan.getRef1FullName())
                .ref1Relationship(loan.getRef1Relationship())
                .ref1Phone(maskPhone(loan.getRef1Phone()))
                .ref1Address(loan.getRef1Address())
                .ref2FullName(loan.getRef2FullName())
                .ref2Relationship(loan.getRef2Relationship())
                .ref2Phone(maskPhone(loan.getRef2Phone()))
                .ref2Address(loan.getRef2Address())
                .build();

        if (loan.getProductId() != null) {
            loanProductService.findProductById(loan.getProductId()).ifPresent(product -> {
                response.setProductCode(product.getCode());
                response.setProductName(product.getName());
            });
        }

        // Thông tin người gọi vốn: lấy từ auth-service, che phone và CCCD
        authServiceClient.getUserById(loan.getBorrowerId()).ifPresent(user -> {
            response.setBorrowerFullName(user.getFullName());
            response.setBorrowerPhone(maskPhone(user.getPhone()));
            response.setBorrowerCccd(maskCccd(user.getCccdNumber()));
        });

        return response;
    }

    /** Che số điện thoại: giữ 3 đầu + 2 cuối, ẩn phần giữa. */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return phone;
        return phone.substring(0, 3) + "•••••" + phone.substring(phone.length() - 2);
    }

    /** Che số CCCD: giữ 3 đầu + 3 cuối, ẩn phần giữa. */
    private static String maskCccd(String cccd) {
        if (cccd == null || cccd.length() < 6) return cccd;
        return cccd.substring(0, 3) + "••••••" + cccd.substring(cccd.length() - 3);
    }

    private boolean hasCmsRole(AuthenticatedUser caller) {
        return caller.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> Set.of("ROLE_ADMIN", "ROLE_OPS", "ROLE_SUPER_ADMIN", "ADMIN", "OPS", "SUPER_ADMIN").contains(role));
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
                java.util.List.of(LoanStatus.ACTIVE));
        long funded  = loanRequestRepository.countByStatusIn(
                java.util.List.of(LoanStatus.FUNDED, LoanStatus.REPAYING, LoanStatus.COMPLETED));
        java.math.BigDecimal activeVol = loanRequestRepository.sumAmountByStatusIn(
                java.util.List.of(LoanStatus.ACTIVE));
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
                .activeFundingVolume(activeVol != null ? activeVol : java.math.BigDecimal.ZERO)
                .totalFundedVolume(totalVol != null ? totalVol : java.math.BigDecimal.ZERO)
                .newLoansToday(todayCount)
                .todayLoanVolume(todayVol != null ? todayVol : java.math.BigDecimal.ZERO)
                .dailyCounts(daily)
                .build();
    }

    @Transactional(readOnly = true)
    public MarketplaceStatsResponse getMarketplaceStats() {
        List<LoanStatus> activeStatuses = List.of(LoanStatus.ACTIVE);
        BigDecimal activeFundingVolume = loanRequestRepository.sumAmountByStatusIn(activeStatuses);
        return MarketplaceStatsResponse.builder()
                .activeLoanCount(loanRequestRepository.countByStatusIn(activeStatuses))
                .activeFundingVolume(activeFundingVolume != null ? activeFundingVolume : BigDecimal.ZERO)
                .build();
    }
}
