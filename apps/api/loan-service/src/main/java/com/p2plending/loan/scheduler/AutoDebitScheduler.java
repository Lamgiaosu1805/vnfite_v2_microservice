package com.p2plending.loan.scheduler;

import com.p2plending.loan.service.RepaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tự động trừ ví người gọi vốn cho kỳ đến hạn hằng ngày.
 *
 * <p>Chạy lúc 00:45 — TRƯỚC job DPD (01:00): kỳ nào ví đủ tiền sẽ được trả (đủ hoặc một phần),
 * phần còn nợ sau đó để job DPD đánh OVERDUE. Mỗi khoản được xử lý trong transaction riêng nên
 * một khoản lỗi (mất kết nối ví, dữ liệu sai...) không chặn các khoản còn lại.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoDebitScheduler {

    private final RepaymentService repaymentService;

    @Scheduled(cron = "${app.auto-debit.cron:0 45 0 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void autoDebitDuePeriods() {
        List<String> loanIds = repaymentService.findAutoDebitLoanIds();
        log.info("Auto-debit scheduler triggered: {} khoản đang trả nợ cần quét", loanIds.size());

        int processed = 0;
        for (String loanId : loanIds) {
            try {
                repaymentService.autoDebitLoan(loanId);
                processed++;
            } catch (Exception e) {
                log.error("Auto-debit loan {} thất bại: {}", loanId, e.getMessage(), e);
            }
        }
        log.info("Auto-debit scheduler done: {}/{} khoản đã quét", processed, loanIds.size());
    }
}
