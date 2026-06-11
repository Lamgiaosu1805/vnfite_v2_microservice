package com.p2plending.credit.service.ai;

import java.util.List;

/**
 * AI risk assessment cho hồ sơ thẩm định.
 *
 * QUAN TRỌNG: kết quả AI chỉ mang tính TƯ VẤN cho admin thẩm định —
 * không bao giờ được dùng để tự động duyệt/từ chối khoản gọi vốn.
 * AI fail → chấm điểm vẫn tiếp tục bình thường (trả null).
 */
public interface AiRiskAssessor {

    /**
     * @param context mô tả hồ sơ (tiếng Việt): profile + khoản vay + breakdown điểm
     * @return đánh giá rủi ro, hoặc null nếu AI tắt/lỗi
     */
    AiRiskAssessment assess(String context);

    /** Kết quả có cấu trúc — schema được Claude SDK tự derive từ record này */
    record AiRiskAssessment(
            String summary,
            List<String> riskFlags,
            String recommendation
    ) {}
}
