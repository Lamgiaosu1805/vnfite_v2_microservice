package com.p2plending.loan.scheduler;

import com.p2plending.loan.service.RepaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Đối soát cộng tiền hoàn trả cho nhà đầu tư bị lỗi: thử lại định kỳ những khoản còn PENDING.
 * Idempotent theo referenceId nên an toàn khi lần trước thực ra đã cộng thành công. Mỗi khoản
 * chạy transaction riêng để một cái lỗi không chặn các cái còn lại.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreditReconcileScheduler {

    private final RepaymentService repaymentService;

    @Scheduled(cron = "${app.credit-reconcile.cron:0 */20 * * * *}", zone = "Asia/Ho_Chi_Minh")
    public void reconcilePendingCredits() {
        List<String> ids = repaymentService.findPendingCreditIds();
        if (ids.isEmpty()) return;

        log.info("Credit reconcile triggered: {} khoản cộng hoàn trả còn PENDING", ids.size());
        int ok = 0;
        for (String id : ids) {
            try {
                repaymentService.reconcileCredit(id);
                ok++;
            } catch (Exception e) {
                log.error("Reconcile credit {} ném ngoài dự kiến: {}", id, e.getMessage(), e);
            }
        }
        log.info("Credit reconcile done: đã xử lý {}/{} khoản", ok, ids.size());
    }
}
