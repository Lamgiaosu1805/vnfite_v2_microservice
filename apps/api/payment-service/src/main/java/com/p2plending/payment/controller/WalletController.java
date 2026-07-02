package com.p2plending.payment.controller;

import com.p2plending.payment.domain.entity.LinkedBank;
import com.p2plending.payment.domain.entity.WithdrawalRequest;
import com.p2plending.payment.domain.enums.WalletOwnerType;
import com.p2plending.payment.domain.repository.LinkedBankRepository;
import com.p2plending.payment.dto.request.WithdrawalConfirmOtpRequest;
import com.p2plending.payment.dto.request.WithdrawalInitiateRequest;
import com.p2plending.payment.dto.response.TransactionResponse;
import com.p2plending.payment.dto.response.WalletResponse;
import com.p2plending.payment.dto.response.WithdrawalResponse;
import com.p2plending.payment.security.AuthenticatedUser;
import com.p2plending.payment.service.KycGuardService;
import com.p2plending.payment.service.WalletService;
import com.p2plending.payment.service.WithdrawalRequestService;
import com.p2plending.payment.service.WithdrawalTransferOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final WithdrawalRequestService withdrawalRequestService;
    private final WithdrawalTransferOrchestrator withdrawalTransferOrchestrator;
    private final LinkedBankRepository linkedBankRepository;
    private final KycGuardService kycGuardService;

    /** Lấy thông tin ví + số dư */
    @GetMapping("/wallet")
    public ResponseEntity<WalletResponse> getWallet(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String ownerType) {
        kycGuardService.requireApproved(user.userId(), "nạp tiền");
        return ResponseEntity.ok(walletService.getWallet(user.userId(), parseOwnerType(ownerType)));
    }

    /** Lịch sử giao dịch */
    @GetMapping("/wallet/transactions")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String ownerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(walletService.getTransactions(user.userId(), parseOwnerType(ownerType), page, size));
    }

    // ─── Luồng rút tiền state machine ────────────────────────────────────────

    /**
     * Bước 1: Tạo withdrawal request, gửi OTP.
     * Response trả về withdrawalId để dùng ở bước tiếp theo.
     */
    @PostMapping("/wallet/withdrawal/initiate")
    public ResponseEntity<Map<String, String>> initiateWithdrawal(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid WithdrawalInitiateRequest req) {
        kycGuardService.requireApproved(user.userId(), "rút tiền");
        WithdrawalRequest wr = withdrawalRequestService.initiate(
                user.userId(), parseOwnerType(req.getOwnerType()), req.getAmount(), req.getLinkedBankId());
        return ResponseEntity.ok(Map.of(
                "withdrawalId", wr.getId(),
                "message", "OTP đã được gửi. Vui lòng xác nhận trong 5 phút."));
    }

    /**
     * Bước 2: Xác nhận OTP, lock tiền, xác định bước tiếp theo.
     */
    @PostMapping("/wallet/withdrawal/{withdrawalId}/confirm-otp")
    public ResponseEntity<WithdrawalResponse> confirmWithdrawalOtp(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String withdrawalId,
            @RequestBody @Valid WithdrawalConfirmOtpRequest req) {
        kycGuardService.requireApproved(user.userId(), "rút tiền");
        WithdrawalRequest wr = withdrawalTransferOrchestrator.confirmOtp(
                user.userId(), withdrawalId, req.getOtp());
        return ResponseEntity.ok(toWithdrawalResponse(wr));
    }

    /**
     * Lấy withdrawal đang active (nếu có) — dùng để khôi phục trạng thái khi app mở lại.
     * Trả 204 No Content nếu không có lệnh nào đang xử lý.
     */
    @GetMapping("/wallet/withdrawal/active")
    public ResponseEntity<WithdrawalResponse> getActiveWithdrawal(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String ownerType) {
        return withdrawalRequestService.getActiveForUser(user.userId(), parseOwnerType(ownerType))
                .map(wr -> ResponseEntity.ok(toWithdrawalResponse(wr)))
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Lấy trạng thái withdrawal.
     */
    @GetMapping("/wallet/withdrawal/{withdrawalId}")
    public ResponseEntity<WithdrawalResponse> getWithdrawal(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String withdrawalId) {
        WithdrawalRequest wr = withdrawalRequestService.getForUser(user.userId(), withdrawalId);
        return ResponseEntity.ok(toWithdrawalResponse(wr));
    }

    /**
     * Gửi lại OTP — chỉ khi withdrawal ở trạng thái OTP_PENDING.
     * Rate limit 60s (kiểm tra trong service).
     */
    @PostMapping("/wallet/withdrawal/{withdrawalId}/resend-otp")
    public ResponseEntity<Map<String, String>> resendOtp(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String withdrawalId) {
        kycGuardService.requireApproved(user.userId(), "rút tiền");
        withdrawalRequestService.resendOtp(user.userId(), withdrawalId);
        return ResponseEntity.ok(Map.of("message", "OTP đã được gửi lại. Vui lòng kiểm tra điện thoại."));
    }

    /**
     * Huỷ withdrawal (chỉ khi chưa lock tiền).
     */
    @DeleteMapping("/wallet/withdrawal/{withdrawalId}/cancel")
    public ResponseEntity<Map<String, String>> cancelWithdrawal(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String withdrawalId) {
        withdrawalRequestService.cancel(user.userId(), withdrawalId);
        return ResponseEntity.ok(Map.of("message", "Yêu cầu rút tiền đã được huỷ."));
    }

    private WithdrawalResponse toWithdrawalResponse(WithdrawalRequest wr) {
        String bankName = "";
        String bankAccountNo = "";
        var bank = linkedBankRepository.findByIdAndIsDeletedFalse(wr.getLinkedBankId());
        if (bank.isPresent()) {
            bankName = bank.get().getBankName();
            bankAccountNo = bank.get().getBankAccountNo();
        }
        return WithdrawalResponse.from(wr, bankName, bankAccountNo);
    }

    private WalletOwnerType parseOwnerType(String ownerType) {
        if (ownerType == null || ownerType.isBlank()) {
            return WalletOwnerType.PERSONAL;
        }
        return WalletOwnerType.valueOf(ownerType.trim().toUpperCase());
    }
}
