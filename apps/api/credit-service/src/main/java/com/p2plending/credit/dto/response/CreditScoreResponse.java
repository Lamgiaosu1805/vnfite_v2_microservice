package com.p2plending.credit.dto.response;

import com.p2plending.credit.domain.entity.DocumentAnalysis;
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

    /** Kết quả AI phân tích từng chứng từ của khoản gọi vốn (rỗng nếu AI tắt hoặc không có chứng từ) */
    private List<DocumentAnalysis> documentAnalyses;

    /**
     * Giải thích nguyên nhân điểm số — sinh tự động từ breakdown, LUÔN có kể cả khi AI tắt/lỗi.
     * Giúp thẩm định viên hiểu vì sao có hạng này và cần làm gì tiếp.
     */
    private ScoreExplanation explanation;

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

    /** Diễn giải "tại sao" có mức điểm này — phục vụ thẩm định, không phải AI. */
    @Data
    @Builder
    public static class ScoreExplanation {
        /** 1 câu chốt: phân biệt điểm thấp do thiếu dữ liệu vs rủi ro thật. */
        private String headline;
        /** Gợi ý hành động kế tiếp (deterministic) cho thẩm định viên. */
        private String suggestedAction;

        private Integer criteriaWithData;
        private Integer criteriaTotal;

        /** Điểm thô mất do tiêu chí CHƯA CÓ dữ liệu (có thể lấy lại khi bổ sung hồ sơ). */
        private Integer pointsLostToMissingData;
        /** Điểm thô mất do tín hiệu rủi ro thực (đã có dữ liệu nhưng rơi nhóm thấp). */
        private Integer pointsLostToWeakSignals;
        /** Ước lượng điểm 300-850 có thể tăng thêm nếu bổ sung đủ dữ liệu còn thiếu. */
        private Integer maxPotentialScoreUplift;

        /** Yếu tố kéo điểm xuống (đã có dữ liệu, điểm dưới ngưỡng). */
        private List<Driver> negativeDrivers;
        /** Điểm mạnh (đạt sát/đủ tối đa). */
        private List<Driver> positiveDrivers;
        /** Tiêu chí thiếu dữ liệu — kèm điểm tiềm năng & cách thu thập. */
        private List<MissingItem> missingData;

        /** Tổng hợp kết quả AI thẩm định chứng từ. */
        private DocumentInsight documents;
    }

    @Data
    @Builder
    public static class Driver {
        private String criteriaName;
        private String component;
        private Integer points;
        private Integer maxPoints;
        private String reason;
    }

    @Data
    @Builder
    public static class MissingItem {
        private String criteriaName;
        private Integer potentialPoints;
        private String howToObtain;
    }

    @Data
    @Builder
    public static class DocumentInsight {
        /** AI thẩm định chứng từ có được bật không. */
        private boolean aiEnabled;
        private Integer total;
        private Integer consistent;
        private Integer suspicious;
        private Integer highRisk;
        private Integer unreadable;
        private Integer errored;
        /** Cảnh báo gộp: điểm bất nhất / file đáng ngờ cần xác minh tay. */
        private List<String> alerts;
        /** 1 dòng trạng thái cho thẩm định viên. */
        private String summary;
    }
}
