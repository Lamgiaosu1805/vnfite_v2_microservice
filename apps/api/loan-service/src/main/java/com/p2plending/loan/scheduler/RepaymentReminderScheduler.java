package com.p2plending.loan.scheduler;

import com.p2plending.loan.service.RepaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nhắc người gọi vốn chuẩn bị số dư trước ngày đến hạn, không trừ tiền. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RepaymentReminderScheduler {

    private final RepaymentService repaymentService;

    @Value("${app.repayment-reminder.days-ahead:1}")
    private int daysAhead;

    /** Chạy 08:00 mỗi ngày theo giờ Việt Nam. */
    @Scheduled(cron = "${app.repayment-reminder.cron:0 0 8 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void remindUpcomingDuePeriods() {
        int sent = repaymentService.publishUpcomingDueReminders(daysAhead);
        log.info("Repayment reminder scheduler done: daysAhead={} sent={}", daysAhead, sent);
    }
}
