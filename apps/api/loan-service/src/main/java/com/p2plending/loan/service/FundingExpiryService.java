package com.p2plending.loan.service;

import com.p2plending.loan.domain.enums.LoanStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Điều phối quét hết hạn: gọi {@link LoanService#expireAndRefund} cho từng khoản (mỗi khoản một
 * transaction riêng qua proxy LoanService nên lỗi một khoản không ảnh hưởng khoản khác).
 * Dùng chung cho cả scheduler ({@code FundingExpiryScheduler}) lẫn nút chạy tay trên CMS.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FundingExpiryService {

    public static final String REASON_FUNDING_EXPIRED =
            "Hết hạn gọi vốn — đã hoàn tiền cho nhà đầu tư";
    public static final String REASON_SIGNING_EXPIRED =
            "Người gọi vốn không hoàn tất ký khế ước — đã hoàn tiền cho nhà đầu tư";
    public static final String REASON_BORROWER_CONFIRMATION_EXPIRED =
            "Người gọi vốn không xác nhận điều khoản sau khi được duyệt";

    private final LoanService loanService;

    /** Kết quả một lần quét hết hạn. */
    public record ExpirySweepResult(int activeExpired, int activeFailed,
                                    int fundedStuck, int fundedFailed,
                                    int awaitingApprovalExpired, int awaitingApprovalFailed) {}

    public ExpirySweepResult runSweep() {
        int[] active = sweep("ACTIVE quá hạn gọi vốn",
                loanService.findExpiredActiveLoanIds(), LoanStatus.ACTIVE, REASON_FUNDING_EXPIRED);
        int[] funded = sweep("FUNDED quá hạn ký khế ước",
                loanService.findStuckFundedLoanIds(), LoanStatus.FUNDED, REASON_SIGNING_EXPIRED);
        int[] awaitingApproval = sweep("AWAITING_BORROWER_APPROVAL quá hạn xác nhận",
                loanService.findExpiredAwaitingBorrowerApprovalLoanIds(),
                LoanStatus.AWAITING_BORROWER_APPROVAL, REASON_BORROWER_CONFIRMATION_EXPIRED);
        return new ExpirySweepResult(active[0], active[1], funded[0], funded[1],
                awaitingApproval[0], awaitingApproval[1]);
    }

    /** @return {ok, failed} */
    private int[] sweep(String label, List<String> ids, LoanStatus expectedStatus, String reason) {
        if (ids.isEmpty()) return new int[]{0, 0};
        log.info("Funding expiry sweep [{}]: {} khoản", label, ids.size());
        int ok = 0, failed = 0;
        for (String id : ids) {
            try {
                loanService.expireAndRefund(id, expectedStatus, reason);
                ok++;
            } catch (Exception e) {
                failed++;
                log.error("Hết hạn khoản {} thất bại: {}", id, e.getMessage(), e);
            }
        }
        log.info("Funding expiry sweep [{}] done: {} thành công, {} lỗi", label, ok, failed);
        return new int[]{ok, failed};
    }
}
