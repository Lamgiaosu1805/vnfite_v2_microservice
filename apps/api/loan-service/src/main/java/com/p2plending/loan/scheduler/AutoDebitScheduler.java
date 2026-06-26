package com.p2plending.loan.scheduler;

import com.p2plending.loan.dto.response.AutoDebitSweepResponse;
import com.p2plending.loan.service.AutoDebitSweepService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tự động trừ ví người gọi vốn cho kỳ đến hạn hằng ngày.
 *
 * <p>Chạy nhiều mốc trong ngày để nếu khách nạp tiền sau lượt quét đầu thì hệ thống vẫn có cơ hội
 * thu trước khi DPD/late fee cập nhật. Mỗi khoản xử lý trong transaction riêng và mỗi lượt quét đều
 * lưu audit summary.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoDebitScheduler {

    private final AutoDebitSweepService autoDebitSweepService;

    @Scheduled(cron = "${app.auto-debit.cron:0 45 0,10,15,22 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void autoDebitDuePeriods() {
        AutoDebitSweepResponse result = autoDebitSweepService.runSweep("SCHEDULER", "system");
        log.info("Auto-debit scheduler done: audit={} scanned={} due={} full={} partial={} amount={} failed={}",
                result.getAuditId(), result.getScannedLoans(), result.getDueLoans(),
                result.getSettledFull(), result.getSettledPartial(),
                result.getAmountCollected(), result.getFailed());
    }
}
