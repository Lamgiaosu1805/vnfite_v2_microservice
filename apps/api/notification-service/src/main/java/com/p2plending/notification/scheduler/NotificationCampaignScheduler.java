package com.p2plending.notification.scheduler;

import com.p2plending.notification.domain.entity.NotificationCampaign;
import com.p2plending.notification.domain.enums.CampaignSendMode;
import com.p2plending.notification.domain.enums.CampaignStatus;
import com.p2plending.notification.domain.repository.NotificationCampaignRepository;
import com.p2plending.notification.service.NotificationCampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Kiểm tra mỗi phút các campaign đặt lịch đến hạn bắn hôm nay (lặp lại hằng ngày
 * trong khoảng ngày đã chọn, vào giờ đã đặt). Dùng >= giờ thay vì so khớp chính
 * xác phút để tự "đuổi kịp" nếu service down đúng lúc đó — vẫn idempotent theo
 * ngày nhờ điều kiện lastSentDate < today.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationCampaignScheduler {

    private static final ZoneId TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    private final NotificationCampaignRepository campaignRepository;
    private final NotificationCampaignService    campaignService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Ho_Chi_Minh")
    public void runDueCampaigns() {
        LocalDate today = LocalDate.now(TZ);
        LocalTime now = LocalTime.now(TZ);

        List<NotificationCampaign> due = campaignRepository.findDueCampaigns(
                CampaignStatus.SCHEDULED, CampaignSendMode.SCHEDULED, today, now);

        for (NotificationCampaign campaign : due) {
            try {
                campaignService.executeCampaign(campaign, today);
            } catch (Exception ex) {
                log.error("[CampaignScheduler] Lỗi chạy campaign {}: {}", campaign.getId(), ex.getMessage());
            }
        }
    }
}
