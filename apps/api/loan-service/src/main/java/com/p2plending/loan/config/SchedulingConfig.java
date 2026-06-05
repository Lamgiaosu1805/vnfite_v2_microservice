package com.p2plending.loan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật @Scheduled cho job DPD. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
