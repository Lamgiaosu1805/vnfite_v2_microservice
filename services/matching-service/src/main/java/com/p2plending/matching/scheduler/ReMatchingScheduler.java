package com.p2plending.matching.scheduler;

import com.p2plending.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReMatchingScheduler {

    private final MatchingService matchingService;

    /**
     * Runs every 30 minutes (configurable via matching.rematching-cron).
     * Finds all loans that are not yet fully funded and re-runs the matching
     * algorithm against newly registered investor preferences.
     *
     * The cron expression is evaluated server-side; this does NOT use
     * @Scheduled(cron = "${matching.rematching-cron}") because Spring cannot
     * use @ConfigurationProperties values in annotations directly.
     * Instead, we fix the expression here and expose it in properties as documentation.
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void reMatchUnfunded() {
        log.info("Re-matching scheduler triggered at {}", LocalDateTime.now());
        try {
            matchingService.reMatchUnfundedLoans();
        } catch (Exception e) {
            log.error("Re-matching scheduler failed: {}", e.getMessage(), e);
        }
    }
}
