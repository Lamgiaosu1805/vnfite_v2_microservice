package com.p2plending.credit.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CreditScoreResponse {

    private String id;
    private String userId;
    private String loanRequestId;

    /** Điểm chuẩn hóa 300-850 */
    private Integer score;

    /** A | B | C | D | E */
    private String grade;

    /** Diễn giải xếp hạng cho admin thẩm định */
    private String gradePolicy;

    private Integer rawPoints;
    private Integer maxPoints;
    private String modelVersion;
    private String status;

    /** Các tiêu chí thiếu dữ liệu (bị chấm 0 điểm) */
    private List<String> missingData;

    private List<ScoreDetailItem> details;

    // ── AI advisory (null nếu AI tắt) ──
    private String aiSummary;
    private List<String> aiRiskFlags;
    private String aiRecommendation;

    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class ScoreDetailItem {
        private String criteriaCode;
        private String criteriaName;
        private String component;
        private String rawValue;
        private Integer points;
        private Integer maxPoints;
    }
}
