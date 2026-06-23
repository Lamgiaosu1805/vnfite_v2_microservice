package com.p2plending.payment.controller;

import com.p2plending.payment.domain.entity.LinkedBank;
import com.p2plending.payment.domain.entity.WithdrawalRequest;
import com.p2plending.payment.domain.repository.LinkedBankRepository;
import com.p2plending.payment.dto.request.WithdrawConfirmRequest;
import com.p2plending.payment.dto.request.WithdrawRequest;
import com.p2plending.payment.dto.request.WithdrawalConfirmOtpRequest;
import com.p2plending.payment.dto.request.WithdrawalInitiateRequest;
import com.p2plending.payment.dto.response.TransactionResponse;
import com.p2plending.payment.dto.response.WalletResponse;
import com.p2plending.payment.dto.response.WithdrawalResponse;
import com.p2plending.payment.security.AuthenticatedUser;
import com.p2plending.payment.service.WalletService;
import com.p2plending.payment.service.WithdrawService;
import com.p2plending.payment.service.WithdrawalRequestService;
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
    private final WithdrawService withdrawService;
    private final WithdrawalRequestService withdrawalRequestService;
    private final LinkedBankRepository linkedBankRepository;

    /** Lấy thông tin ví + số dư */
    @GetMapping("/wallet")
    public ResponseEntity<WalletResponse> getWallet(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(walletService.getWallet(user.userId()));
    }

    /** Lịch sử giao dịch */
    @GetMapping("/wallet/transactions")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(walletService.getTransactions(user.userId(), page, size));
    }

    /**
     * Bước 1 rút tiền: kiểm tra số dư, lưu pending vào Redis, gửi OTP.
     * Response 200 = OTP đã gửi.
     */
    @PostMapping("/wallet/withdraw")
    public ResponseEntity<Map<String, String>> initiateWithdraw(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid WithdrawRequest req) {
        withdrawService.initiateWithdraw(user.userId(), req);
        return ResponseEntity.ok(Map.of("message", "OTP đã được gửi. Vui lòng xác nhận trong 5 phút."));
    }

    /**
     * Bước 2 rút tiền: xác thực OTP, thực hiện chuyển tiền.
     */
    @PostMapping("/wallet/withdraw/confirm")
    public ResponseEntity<Map<String, String>> confirmWithdraw(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid WithdrawConfirmRequest req) {
        withdrawService.confirmWithdraw(user.userId(), req.getOtp());
        return ResponseEntity.ok(Map.of("message", "Yêu cầu rút tiền đã được ghi nhận. Tiền sẽ về tài khoản trong vài phút."));
    }

    // ─── Luồng rút tiền mới (v2) ──────────────────────────────────────────────

    /**
     * [v2] Bước 1: Tạo withdrawal request, gửi OTP.
     * Response trả về withdrawalId để dùng ở bước tiếp theo.
     */
    @PostMapping("/wallet/withdrawal/initiate")
    public ResponseEntity<Map<String, String>> initiateWithdrawal(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid WithdrawalInitiateRequest req) {
        WithdrawalRequest wr = withdrawalRequestService.initiate(
                user.userId(), req.getAmount(), req.getLinkedBankId());
        return ResponseEntity.ok(Map.of(
                "withdrawalId", wr.getId(),
                "message", "OTP đã được gửi. Vui lòng xác nhận trong 5 phút."));
    }

    /**
     * [v2] Bước 2: Xác nhận OTP, lock tiền, xác định bước tiếp theo.
     */
    @PostMapping("/wallet/withdrawal/{withdrawalId}/confirm-otp")
    public ResponseEntity<WithdrawalResponse> confirmWithdrawalOtp(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String withdrawalId,
            @RequestBody @Valid WithdrawalConfirmOtpRequest req) {
        WithdrawalRequest wr = withdrawalRequestService.confirmOtp(
                user.userId(), withdrawalId, req.getOtp());
        return ResponseEntity.ok(toWithdrawalResponse(wr));
    }

    /**
     * [v2] Lấy trạng thái withdrawal.
     */
    @GetMapping("/wallet/withdrawal/{withdrawalId}")
    public ResponseEntity<WithdrawalResponse> getWithdrawal(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String withdrawalId) {
        WithdrawalRequest wr = withdrawalRequestService.getForUser(user.userId(), withdrawalId);
        return ResponseEntity.ok(toWithdrawalResponse(wr));
    }

    /**
     * [v2] Huỷ withdrawal (chỉ khi chưa lock tiền).
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
        linkedBankRepository.findByIdAndIsDeletedFalse(wr.getLinkedBankId()).ifPresent(b -> {});
        var bank = linkedBankRepository.findByIdAndIsDeletedFalse(wr.getLinkedBankId());
        if (bank.isPresent()) {
            bankName = bank.get().getBankName();
            bankAccountNo = bank.get().getBankAccountNo();
        }
        return WithdrawalResponse.from(wr, bankName, bankAccountNo);
    }
}
