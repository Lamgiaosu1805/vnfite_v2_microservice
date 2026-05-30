package com.p2plending.matching.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "matching")
@Data
public class MatchingProperties {

    /** Minimum score [0.0–1.0] to create a MatchRecord. */
    private double minScoreThreshold = 0.60;

    /** Maximum matches published per loan per run. */
    private int maxMatchesPerLoan = 10;

    /** Cron expression for the re-matching scheduler. */
    private String rematchingCron = "0 */30 * * * *";
}
