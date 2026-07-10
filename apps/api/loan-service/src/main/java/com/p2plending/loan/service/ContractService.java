package com.p2plending.loan.service;

import com.p2plending.loan.client.AuthServiceClient;
import com.p2plending.loan.client.PaymentServiceClient;
import com.p2plending.loan.config.CacheConfig;
import com.p2plending.loan.config.RedisNamespaceProperties;
import com.p2plending.loan.domain.entity.LoanContract;
import com.p2plending.loan.domain.entity.LoanOffer;
import com.p2plending.loan.domain.entity.LoanProduct;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.enums.ContractStatus;
import com.p2plending.loan.domain.enums.ContractType;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.OfferStatus;
import com.p2plending.loan.domain.enums.ProductCategory;
import com.p2plending.loan.domain.repository.LoanContractRepository;
import com.p2plending.loan.domain.repository.LoanOfferRepository;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.dto.response.ContractResponse;
import com.p2plending.loan.dto.response.ContractSignInitResponse;
import com.p2plending.loan.exception.InvalidLoanStateException;
import com.p2plending.loan.exception.LoanNotFoundException;
import com.p2plending.loan.kafka.KafkaProducerService;
import com.p2plending.loan.kafka.event.InvestmentRefundedEvent;
import com.p2plending.loan.service.contract.ContractSignatureProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quản lý hợp đồng điện tử (mock VNPT) cho luồng P2P.
 *
 * <p>Ký bằng OTP — OTP scope theo từng hợp đồng (purpose = ký HĐ), lưu Redis namespaced
 * (key {@code ...:loan-service:contract_sign:{contractId}}), TTL 10 phút, mock {@code 000000}.
 *
 * <p>Phụ thuộc một chiều: {@code LoanService → ContractService → (repos, provider, kafka)} —
 * side-effect đổi trạng thái khoản/offer được xử lý trực tiếp tại đây để tránh phụ thuộc vòng.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContractService {

    private static final ZoneId TZ = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String KEY_PREFIX = "contract_sign:";
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final String MOCK_OTP = "000000";

    private final LoanContractRepository   contractRepository;
    private final LoanRequestRepository    loanRequestRepository;
    private final LoanOfferRepository      loanOfferRepository;
    private final ContractSignatureProvider signatureProvider;
    private final KafkaProducerService     kafkaProducerService;
    private final StringRedisTemplate      redisTemplate;
    private final RedisNamespaceProperties redisNamespaceProperties;
    private final CacheManager             cacheManager;
    private final PaymentServiceClient     paymentServiceClient;
    private final AuthServiceClient        authServiceClient;
    private final VnfOtpSenderService      vnfOtpSenderService;
    private final LoanProductService       loanProductService;

    @Value("${app.otp.mock:true}")
    private boolean mockOtp;

    // ── Phát hành hợp đồng ────────────────────────────────────────

    /** Phát hành hợp đồng đầu tư cho một offer (PENDING_SIGNATURE). */
    @Transactional
    public LoanContract issueInvestmentContract(LoanRequest loan, LoanOffer offer) {
        LoanContract contract = LoanContract.builder()
                .loanId(loan.getId())
                .contractType(ContractType.INVESTMENT)
                .partyId(offer.getInvestorId())
                .offerId(offer.getId())
                .amount(offer.getAmount())
                .interestRate(loan.getInterestRate())
                .termMonths(loan.getTermMonths())
                .provider("MOCK_VNPT")
                .status(ContractStatus.PENDING_SIGNATURE)
                .issuedAt(LocalDateTime.now(TZ))
                .build();
        contract = contractRepository.save(contract);

        ContractSignatureProvider.IssueResult issued = signatureProvider.issue(contract);
        contract.setProviderContractId(issued.providerContractId());
        contract.setDocumentUrl(issued.documentUrl());
        contract.setContractNo(buildContractNo(loan, ContractType.INVESTMENT, contract.getId()));
        contract = contractRepository.save(contract);

        log.info("Issued INVESTMENT contract {} for loan {} offer {} investor {}",
                contract.getId(), loan.getId(), offer.getId(), offer.getInvestorId());
        return contract;
    }

    /**
     * Phát hành hợp đồng vay (khế ước nhận nợ) cho người gọi vốn khi khoản FUNDED.
     * Idempotent — bỏ qua nếu đã có hợp đồng vay chưa xóa.
     */
    @Transactional
    public LoanContract issueLoanAgreement(LoanRequest loan) {
        List<LoanContract> existing = contractRepository
                .findByLoanIdAndContractTypeAndIsDeletedFalse(loan.getId(), ContractType.LOAN_AGREEMENT);
        if (!existing.isEmpty()) {
            log.warn("Loan agreement already exists for loan {} — skip issuing", loan.getId());
            return existing.get(0);
        }

        LoanContract contract = LoanContract.builder()
                .loanId(loan.getId())
                .contractType(ContractType.LOAN_AGREEMENT)
                .partyId(loan.getBorrowerId())
                .amount(loan.getAmount())
                .interestRate(loan.getInterestRate())
                .termMonths(loan.getTermMonths())
                .provider("MOCK_VNPT")
                .status(ContractStatus.PENDING_SIGNATURE)
                .issuedAt(LocalDateTime.now(TZ))
                .build();
        applyBorrowerPartyIdentity(contract, loan);
        contract = contractRepository.save(contract);

        ContractSignatureProvider.IssueResult issued = signatureProvider.issue(contract);
        contract.setProviderContractId(issued.providerContractId());
        contract.setDocumentUrl(issued.documentUrl());
        contract.setContractNo(buildContractNo(loan, ContractType.LOAN_AGREEMENT, contract.getId()));
        contract = contractRepository.save(contract);

        kafkaProducerService.publishContractReady(loan, contract);
        log.info("Issued LOAN_AGREEMENT contract {} for loan {} borrower {}",
                contract.getId(), loan.getId(), loan.getBorrowerId());
        return contract;
    }

    // ── Ký hợp đồng (OTP) ─────────────────────────────────────────

    /** Bước 1: gửi OTP để ký hợp đồng. */
    @Transactional(readOnly = true)
    public ContractSignInitResponse initSign(String contractId, String userId) {
        LoanContract contract = requireOwnedContract(contractId, userId);
        if (contract.getStatus() != ContractStatus.PENDING_SIGNATURE) {
            throw new InvalidLoanStateException("Hợp đồng đã được ký hoặc không ở trạng thái chờ ký.");
        }

        String otp = resolveOtp(userId);
        redisTemplate.opsForValue().set(otpKey(contractId), otp, OTP_TTL);

        ContractSignInitResponse.ContractSignInitResponseBuilder builder = ContractSignInitResponse.builder()
                .message("Mã OTP ký hợp đồng đã được gửi đến số điện thoại của bạn. Có hiệu lực trong 10 phút.");
        if (mockOtp) builder.otp(otp);
        return builder.build();
    }

    /** Bước 2: xác thực OTP → ký hợp đồng → áp side-effect theo loại. */
    @Transactional
    public ContractResponse signContract(String contractId, String userId, String otp) {
        LoanContract contract = requireOwnedContract(contractId, userId);
        if (contract.getStatus() != ContractStatus.PENDING_SIGNATURE) {
            throw new InvalidLoanStateException("Hợp đồng đã được ký hoặc không ở trạng thái chờ ký.");
        }

        String key = otpKey(contractId);
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phiên ký đã hết hạn. Vui lòng yêu cầu mã OTP mới.");
        }
        if (!stored.equals(otp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã OTP không đúng. Vui lòng kiểm tra lại.");
        }

        LoanRequest loan = loanRequestRepository.findById(contract.getLoanId())
                .orElseThrow(() -> new LoanNotFoundException(contract.getLoanId()));

        // Hợp đồng đầu tư: re-validate trước khi ký để tránh vượt số tiền còn lại.
        if (contract.getContractType() == ContractType.INVESTMENT) {
            validateInvestmentStillFits(loan, contract);
        }

        redisTemplate.delete(key);

        ContractSignatureProvider.SignResult signed = signatureProvider.sign(contract);
        contract.setSignedDocumentUrl(signed.signedDocumentUrl());
        contract.setSignedAt(signed.signedAt());
        contract.setSignedVia("OTP");
        contract.setStatus(ContractStatus.SIGNED);
        contractRepository.save(contract);

        if (contract.getContractType() == ContractType.INVESTMENT) {
            applyInvestmentSigned(loan, contract);
        } else {
            applyLoanAgreementSigned(loan);
        }

        evictLoanCaches(loan.getId());
        // reload loan để phản ánh trạng thái mới trong response
        LoanRequest fresh = loanRequestRepository.findById(loan.getId()).orElse(loan);
        return toResponse(contract, fresh);
    }

    /** CMS xác nhận khế ước giấy đã ký; giữ riêng với luồng OTP để có thể bật ký số sau này. */
    @Transactional
    public ContractResponse confirmPaperSignature(String contractId, String confirmedBy) {
        LoanContract contract = contractRepository.findByIdAndIsDeletedFalse(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khế ước."));
        LoanRequest loan = loanRequestRepository.findById(contract.getLoanId())
                .orElseThrow(() -> new LoanNotFoundException(contract.getLoanId()));
        if (contract.getStatus() == ContractStatus.SIGNED) return toResponse(contract, loan);
        if (contract.getStatus() != ContractStatus.PENDING_SIGNATURE) {
            throw new InvalidLoanStateException("Khế ước không ở trạng thái chờ ký giấy.");
        }
        if (contract.getContractType() == ContractType.INVESTMENT) validateInvestmentStillFits(loan, contract);
        contract.setStatus(ContractStatus.SIGNED);
        contract.setSignedVia("PAPER");
        contract.setSignedAt(LocalDateTime.now(TZ));
        contractRepository.save(contract);
        if (contract.getContractType() == ContractType.INVESTMENT) applyInvestmentSigned(loan, contract);
        else applyLoanAgreementSigned(loan);
        evictLoanCaches(loan.getId());
        log.info("Paper contract confirmed: contract={} by={}", contractId, confirmedBy);
        return toResponse(contract, loanRequestRepository.findById(loan.getId()).orElse(loan));
    }

    private void validateInvestmentStillFits(LoanRequest loan, LoanContract contract) {
        LoanOffer offer = loanOfferRepository.findById(contract.getOfferId())
                .orElseThrow(() -> new InvalidLoanStateException("Không tìm thấy lệnh đầu tư của hợp đồng này."));
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new InvalidLoanStateException("Lệnh đầu tư không còn hiệu lực để ký.");
        }
        BigDecimal accepted = loanOfferRepository
                .sumAmountByLoanRequestIdAndStatus(loan.getId(), OfferStatus.ACCEPTED);
        BigDecimal remaining = loan.getAmount().subtract(accepted == null ? BigDecimal.ZERO : accepted);
        if (offer.getAmount().compareTo(remaining) > 0) {
            // Hết slice — void cả offer lẫn hợp đồng.
            offer.setStatus(OfferStatus.CANCELLED);
            loanOfferRepository.save(offer);
            contract.setStatus(ContractStatus.VOIDED);
            contractRepository.save(contract);
            throw new InvalidLoanStateException(
                    "Khoản đã đủ vốn hoặc số tiền còn lại đã thay đổi (còn %,.0f VNĐ). Không thể hoàn tất đầu tư này."
                            .formatted(remaining));
        }
    }

    private void applyInvestmentSigned(LoanRequest loan, LoanContract contract) {
        LoanOffer offer = loanOfferRepository.findById(contract.getOfferId())
                .orElseThrow(() -> new InvalidLoanStateException("Không tìm thấy lệnh đầu tư của hợp đồng này."));
        offer.setStatus(OfferStatus.ACCEPTED);
        loanOfferRepository.save(offer);

        // Khóa tiền trong ví nhà đầu tư (đúng tư cách của lệnh) — cam kết vốn tại thời điểm ký hợp đồng.
        // lockAmount re-validate số dư khả dụng; nếu không đủ sẽ ném lỗi → toàn bộ giao dịch ký rollback.
        paymentServiceClient.lock(offer.getInvestorId(), offer.getOwnerType(), offer.getAmount(),
                "Đầu tư khoản gọi vốn " + loan.getLoanCode(),
                "LOCK-" + offer.getId());

        // fundedAmount = tổng các offer ACCEPTED (nguồn sự thật duy nhất).
        BigDecimal accepted = loanOfferRepository
                .sumAmountByLoanRequestIdAndStatus(loan.getId(), OfferStatus.ACCEPTED);
        loan.setFundedAmount(accepted == null ? BigDecimal.ZERO : accepted);

        if (loan.isFullyFunded() && loan.getStatus() == LoanStatus.ACTIVE) {
            loan.setStatus(LoanStatus.FUNDED);
            loan.setFundedAt(LocalDateTime.now(TZ));
            loanRequestRepository.save(loan);
            issueLoanAgreement(loan);
            kafkaProducerService.publishLoanFunded(loan);
            log.info("Loan {} fully funded after investment contract {} signed — agreement issued, loan.funded published",
                    loan.getId(), contract.getId());
        } else {
            loanRequestRepository.save(loan);
            log.info("Investment contract {} signed — offer {} accepted, fundedAmount={}",
                    contract.getId(), offer.getId(), loan.getFundedAmount());
        }
    }

    /**
     * Hoàn tiền cho toàn bộ nhà đầu tư của một khoản (unlock các offer ACCEPTED) và void các
     * hợp đồng đầu tư chưa ký. Dùng khi khoản bị hủy/hết hạn gọi vốn. Idempotent theo offerId
     * (unlock dùng referenceId "REFUND-{offerId}"), nên chạy lại an toàn không hoàn trùng.
     */
    @Transactional
    public void refundInvestorsAndVoid(LoanRequest loan, String reason) {
        List<LoanOffer> accepted = loanOfferRepository
                .findByLoanRequestIdAndStatus(loan.getId(), OfferStatus.ACCEPTED);

        List<InvestmentRefundedEvent.Refund> refunds = new ArrayList<>();
        for (LoanOffer offer : accepted) {
            paymentServiceClient.unlock(offer.getInvestorId(), offer.getOwnerType(), offer.getAmount(),
                    reason + " " + loan.getLoanCode(),
                    "REFUND-" + offer.getId());
            offer.setStatus(OfferStatus.CANCELLED);
            loanOfferRepository.save(offer);
            refunds.add(InvestmentRefundedEvent.Refund.builder()
                    .investorId(offer.getInvestorId())
                    .amount(offer.getAmount())
                    .build());
        }

        // Trước giải ngân: nếu khoản bị hủy/hết hạn, mọi hợp đồng chưa giải ngân đều không còn hiệu lực.
        contractRepository.findByLoanIdAndIsDeletedFalseOrderByCreatedAtDesc(loan.getId()).stream()
                .filter(c -> c.getStatus() != ContractStatus.VOIDED)
                .forEach(c -> {
                    c.setStatus(ContractStatus.VOIDED);
                    contractRepository.save(c);
                });

        // Thông báo hoàn tiền cho từng nhà đầu tư (chỉ phát khi có refund).
        kafkaProducerService.publishInvestmentRefunded(loan, reason, refunds);

        log.info("Refunded {} offers and voided pending contracts for loan {} ({})",
                refunds.size(), loan.getId(), reason);
    }

    private void applyLoanAgreementSigned(LoanRequest loan) {
        if (loan.getStatus() != LoanStatus.FUNDED) {
            throw new InvalidLoanStateException(
                    "Khoản gọi vốn %s không ở trạng thái chờ ký hợp đồng vay (status: %s)"
                            .formatted(loan.getLoanCode(), loan.getStatus()));
        }
        loan.setStatus(LoanStatus.AWAITING_DISBURSEMENT);
        loanRequestRepository.save(loan);
        log.info("Loan agreement signed — loan {} → AWAITING_DISBURSEMENT", loan.getId());
    }

    // ── Đọc hợp đồng ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContractResponse> getMyContracts(String userId, String loanIdFilter) {
        List<LoanContract> contracts = (loanIdFilter != null && !loanIdFilter.isBlank())
                ? contractRepository.findByPartyIdAndLoanIdAndIsDeletedFalseOrderByCreatedAtDesc(userId, loanIdFilter)
                : contractRepository.findByPartyIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
        if (contracts.isEmpty()) return List.of();

        Set<String> loanIds = contracts.stream().map(LoanContract::getLoanId).collect(Collectors.toSet());
        Map<String, LoanRequest> loanMap = loanRequestRepository.findAllById(loanIds).stream()
                .collect(Collectors.toMap(LoanRequest::getId, l -> l));

        return contracts.stream()
                .map(c -> toResponse(c, loanMap.get(c.getLoanId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractResponse getContract(String contractId, String userId) {
        LoanContract contract = requireOwnedContract(contractId, userId);
        LoanRequest loan = loanRequestRepository.findById(contract.getLoanId()).orElse(null);
        return toResponse(contract, loan);
    }

    /** Dùng cho CMS — list mọi hợp đồng của một khoản (không lọc theo party). */
    @Transactional(readOnly = true)
    public List<ContractResponse> getContractsByLoan(String loanId) {
        LoanRequest loan = loanRequestRepository.findById(loanId).orElse(null);
        return contractRepository.findByLoanIdAndIsDeletedFalseOrderByCreatedAtDesc(loanId).stream()
                .map(c -> toResponse(c, loan))
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private LoanContract requireOwnedContract(String contractId, String userId) {
        LoanContract contract = contractRepository.findByIdAndIsDeletedFalse(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng."));
        if (!contract.getPartyId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với hợp đồng này.");
        }
        return contract;
    }

    /**
     * Snapshot định danh Bên A vào khế ước. Khoản sản phẩm doanh nghiệp (BUSINESS/ENTERPRISE):
     * người gọi vốn ký với tư cách PHÁP NHÂN → ghi tên DN + số ĐKKD/MST + người đại diện. Khoản cá
     * nhân, hoặc không lấy được hồ sơ DN đã duyệt → giữ tư cách cá nhân (an toàn, không chặn phát hành).
     */
    private void applyBorrowerPartyIdentity(LoanContract contract, LoanRequest loan) {
        if (!isBusinessCategory(loan)) {
            contract.setPartyType("PERSONAL");
            return;
        }
        var profile = authServiceClient.getBusinessProfile(loan.getBorrowerId())
                .filter(AuthServiceClient.BusinessProfileInfo::isApproved)
                .filter(p -> p.businessName() != null && !p.businessName().isBlank());
        if (profile.isEmpty()) {
            contract.setPartyType("PERSONAL");
            log.warn("Loan {} thuộc sản phẩm DN nhưng không lấy được hồ sơ DN đã duyệt của borrower {} — "
                    + "khế ước để tư cách cá nhân", loan.getId(), loan.getBorrowerId());
            return;
        }
        AuthServiceClient.BusinessProfileInfo p = profile.get();
        contract.setPartyType("BUSINESS");
        contract.setPartyName(p.businessName());
        contract.setPartyIdentityNo(p.taxCode() != null && !p.taxCode().isBlank()
                ? p.taxCode() : p.registrationNumber());
        contract.setPartyRepresentative(p.representativeName());
        log.info("Loan {} khế ước ký tư cách pháp nhân: {} (ĐKKD/MST {})",
                loan.getId(), p.businessName(), contract.getPartyIdentityNo());
    }

    private boolean isBusinessCategory(LoanRequest loan) {
        if (loan.getProductId() == null || loan.getProductId().isBlank()) {
            return false;
        }
        return loanProductService.findProductById(loan.getProductId())
                .map(LoanProduct::getCategory)
                .map(category -> category == ProductCategory.BUSINESS || category == ProductCategory.ENTERPRISE)
                .orElse(false);
    }

    private String buildContractNo(LoanRequest loan, ContractType type, String contractId) {
        String base = loan.getLoanCode() != null ? loan.getLoanCode() : "VNF";
        String suffix = type == ContractType.LOAN_AGREEMENT ? "LOAN" : "INV";
        String tail = contractId.replace("-", "");
        tail = tail.substring(Math.max(0, tail.length() - 6)).toUpperCase();
        return "%s-HD-%s-%s".formatted(base, suffix, tail);
    }

    /** Public mapper — dùng bởi LoanService khi tạo offer. */
    public ContractResponse toContractResponse(LoanContract c, LoanRequest loan) {
        return toResponse(c, loan);
    }

    private ContractResponse toResponse(LoanContract c, LoanRequest loan) {
        return ContractResponse.builder()
                .id(c.getId())
                .loanId(c.getLoanId())
                .loanCode(loan != null ? loan.getLoanCode() : null)
                .loanStatus(loan != null ? loan.getStatus() : null)
                .contractType(c.getContractType())
                .partyId(c.getPartyId())
                .partyType(c.getPartyType())
                .partyName(c.getPartyName())
                .partyIdentityNo(c.getPartyIdentityNo())
                .partyRepresentative(c.getPartyRepresentative())
                .offerId(c.getOfferId())
                .contractNo(c.getContractNo())
                .amount(c.getAmount())
                .interestRate(c.getInterestRate())
                .termMonths(c.getTermMonths())
                .provider(c.getProvider())
                .documentUrl(c.getDocumentUrl())
                .signedDocumentUrl(c.getSignedDocumentUrl())
                .status(c.getStatus())
                .issuedAt(c.getIssuedAt())
                .signedAt(c.getSignedAt())
                .signedVia(c.getSignedVia())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private void evictLoanCaches(String loanId) {
        Cache byId = cacheManager.getCache(CacheConfig.CACHE_LOAN_BY_ID);
        if (byId != null) byId.evict(loanId);
        Cache list = cacheManager.getCache(CacheConfig.CACHE_LOANS);
        if (list != null) list.clear();
    }

    private String otpKey(String contractId) {
        return redisNamespaceProperties.qualify(KEY_PREFIX + contractId);
    }

    private String resolveOtp(String userId) {
        if (mockOtp) {
            return MOCK_OTP;
        }

        String phone = authServiceClient.getUserById(userId)
                .map(user -> user.getPhone())
                .filter(phoneNumber -> phoneNumber != null && !phoneNumber.isBlank())
                .orElseThrow(() -> new InvalidLoanStateException(
                        "Không lấy được số điện thoại để gửi OTP. Vui lòng thử lại."));

        String sentOtp = vnfOtpSenderService.sendContractOtp(phone);
        if (sentOtp == null || sentOtp.isBlank()) {
            throw new InvalidLoanStateException(
                    "Không gửi được OTP ký hợp đồng. Vui lòng thử lại sau.");
        }
        return sentOtp;
    }
}
