package com.p2plending.loan.scheduler;

import com.p2plending.loan.service.RepaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Quét DPD hàng ngày — cập nhật quá hạn và đánh DEFAULTED khi vượt ngưỡng. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DpdScheduler {

    private final RepaymentService repaymentService;

    /** Chạy 01:00 mỗi ngày theo giờ Việt Nam. */
    @Scheduled(cron = "${app.dpd.cron:0 0 1 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void sweepDpd() {
        log.info("DPD scheduler triggered");
        repaymentService.runDpdSweep();
    }
}
