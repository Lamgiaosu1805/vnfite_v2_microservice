package com.p2plending.payment.service;

import com.p2plending.payment.domain.entity.ManualDepositRequest;
import com.p2plending.payment.domain.entity.Wallet;
import com.p2plending.payment.domain.enums.ManualDepositStatus;
import com.p2plending.payment.domain.enums.WalletOwnerType;
import com.p2plending.payment.domain.repository.ManualDepositRequestRepository;
import com.p2plending.payment.domain.repository.WalletRepository;
import com.p2plending.payment.domain.repository.WalletTransactionRepository;
import com.p2plending.payment.dto.request.ManualDepositCreateRequest;
import com.p2plending.payment.dto.response.ManualDepositResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManualDepositService {

    private final ManualDepositRequestRepository requestRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletService walletService;
    private final TikluyClient tikluyClient;

    @Transactional
    public ManualDepositResponse create(String userId, ManualDepositCreateRequest input) {
        WalletOwnerType ownerType = input.getOwnerType() == null ? WalletOwnerType.PERSONAL : input.getOwnerType();
        Wallet wallet = walletRepository.findByUserIdAndOwnerTypeAndIsDeletedFalse(userId, ownerType)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy ví VNFITE để nạp tiền."));
        BigDecimal amount = vnd(input.getAmount());

        ManualDepositRequest request = requestRepository.save(ManualDepositRequest.builder()
                .walletId(wallet.getId())
                .userId(userId)
                .ownerType(ownerType)
                .amount(amount)
                .billFileId(input.getBillFileId().trim())
                .billFileName(input.getBillFileName().trim())
                .status(ManualDepositStatus.PENDING)
                .build());
        log.info("Manual deposit requested: request={} user={} wallet={} amount={}",
                request.getId(), userId, wallet.getId(), amount);
        return ManualDepositResponse.from(request);
    }

    @Transactional(readOnly = true)
    public Page<ManualDepositResponse> getMine(String userId, int page, int size) {
        return requestRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(ManualDepositResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ManualDepositResponse> getForCms(ManualDepositStatus status, int page, int size) {
        return requestRepository.findForCms(status, PageRequest.of(page, size)).map(ManualDepositResponse::from);
    }

    /**
     * Cộng tiền tại TIKLUY trước, rồi ghi ledger VNFITE với reference cố định theo request.
     * Nếu request bị retry sau lỗi giữa chừng, TIKLUY và wallet ledger đều idempotent theo reference này.
     */
    @Transactional
    public ManualDepositResponse approve(String requestId, String reviewedBy) {
        ManualDepositRequest request = findPendingForDecision(requestId);
        Wallet wallet = walletRepository.findById(request.getWalletId())
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new IllegalStateException("Ví của yêu cầu nạp tiền không còn tồn tại."));
        String reference = "MANUAL-BILL-" + request.getId();

        tikluyClient.creditManualDeposit(reference, wallet.getVnfAccountNo(), request.getAmount());
        walletService.processDeposit(reference, wallet.getVnfAccountNo(), request.getAmount(), reference,
                null, "Nạp tiền theo bill đã được CMS phê duyệt");

        request.setStatus(ManualDepositStatus.APPROVED);
        request.setReviewedBy(blankToNull(reviewedBy));
        request.setReviewedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        request.setWalletTransactionId(walletTransactionRepository.findByReferenceId(reference)
                .map(item -> item.getId()).orElse(null));
        requestRepository.save(request);
        log.info("Manual deposit approved: request={} by={} amount={}", requestId, reviewedBy, request.getAmount());
        return ManualDepositResponse.from(request);
    }

    @Transactional
    public ManualDepositResponse reject(String requestId, String reviewedBy, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập lý do từ chối bill nạp tiền.");
        }
        ManualDepositRequest request = findPendingForDecision(requestId);
        request.setStatus(ManualDepositStatus.REJECTED);
        request.setRejectionReason(reason.trim());
        request.setReviewedBy(blankToNull(reviewedBy));
        request.setReviewedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        requestRepository.save(request);
        log.info("Manual deposit rejected: request={} by={}", requestId, reviewedBy);
        return ManualDepositResponse.from(request);
    }

    private ManualDepositRequest findPendingForDecision(String requestId) {
        ManualDepositRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu nạp tiền."));
        if (request.getStatus() != ManualDepositStatus.PENDING) {
            throw new IllegalStateException("Yêu cầu nạp tiền này đã được xử lý trước đó.");
        }
        return request;
    }

    private BigDecimal vnd(BigDecimal amount) {
        if (amount == null || amount.compareTo(new BigDecimal("1000")) < 0) {
            throw new IllegalArgumentException("Số tiền nạp tối thiểu là 1.000 VNĐ.");
        }
        BigDecimal normalized = amount.setScale(0, RoundingMode.HALF_UP);
        if (amount.compareTo(normalized) != 0) {
            throw new IllegalArgumentException("Số tiền nạp phải là số VND nguyên.");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
