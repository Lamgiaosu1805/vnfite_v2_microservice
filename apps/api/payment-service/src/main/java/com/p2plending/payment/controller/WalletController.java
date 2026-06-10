package com.p2plending.payment.controller;

import com.p2plending.payment.dto.request.WithdrawConfirmRequest;
import com.p2plending.payment.dto.request.WithdrawRequest;
import com.p2plending.payment.dto.response.TransactionResponse;
import com.p2plending.payment.dto.response.WalletResponse;
import com.p2plending.payment.security.AuthenticatedUser;
import com.p2plending.payment.service.WalletService;
import com.p2plending.payment.service.WithdrawService;
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
}
