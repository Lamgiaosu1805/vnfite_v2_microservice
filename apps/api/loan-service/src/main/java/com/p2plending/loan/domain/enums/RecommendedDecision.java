package com.p2plending.loan.domain.enums;

/**
 * Khuyến nghị của hệ thống cho thẩm định viên. Chỉ mang tính hỗ trợ —
 * quyết định cuối cùng thuộc thẩm định viên & ban lãnh đạo.
 */
public enum RecommendedDecision {
    APPROVE, // nghiêng về đề xuất duyệt
    REVIEW,  // cần thẩm định kỹ / bổ sung trước khi quyết
    REJECT   // nghiêng về từ chối
}
