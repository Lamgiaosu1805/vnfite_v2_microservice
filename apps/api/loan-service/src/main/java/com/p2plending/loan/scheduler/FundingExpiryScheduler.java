package com.p2plending.loan.scheduler;

import com.p2plending.loan.service.FundingExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lịch quét hết hạn hằng ngày: khoản ACTIVE quá hạn gọi vốn và khoản FUNDED quá hạn ký khế ước
 * sẽ được hoàn tiền cho nhà đầu tư và chuyển sang CANCELLED. Logic nằm ở {@link FundingExpiryService}
 * (dùng chung với nút chạy tay trên CMS).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FundingExpiryScheduler {

    private final FundingExpiryService fundingExpiryService;

    /** Chạy 01:30 mỗi ngày theo giờ Việt Nam. */
    @Scheduled(cron = "${app.funding.expiry-cron:0 30 1 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void sweepExpiredFunding() {
        FundingExpiryService.ExpirySweepResult result = fundingExpiryService.runSweep();
        log.info("Scheduled funding expiry sweep: activeExpired={} fundedStuck={}",
                result.activeExpired(), result.fundedStuck());
    }
}
