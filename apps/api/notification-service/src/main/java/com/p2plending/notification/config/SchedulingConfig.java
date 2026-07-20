package com.p2plending.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật @Scheduled cho NotificationCampaignScheduler (bắn thông báo marketing đặt lịch). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
