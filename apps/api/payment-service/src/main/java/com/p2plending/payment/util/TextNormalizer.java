package com.p2plending.payment.util;

import java.text.Normalizer;

public final class TextNormalizer {

    private TextNormalizer() {}

    /**
     * Chuyển tên tiếng Việt về dạng không dấu, in hoa, chuẩn hóa khoảng trắng.
     * Dùng để so sánh tên chủ tài khoản MB Bank (trả về không dấu) với fullName KYC.
     * Ví dụ: "Nghiêm Khắc Lâm" → "NGHIEM KHAC LAM"
     */
    public static String normalize(String text) {
        if (text == null || text.isBlank()) return "";
        String s = text.trim().toUpperCase();
        // Đ không bị NFD decompose — xử lý trước
        s = s.replace('Đ', 'D');
        // NFD decompose rồi xóa combining diacritical marks
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Chuẩn hóa khoảng trắng thừa
        return s.replaceAll("\\s+", " ").trim();
    }
}
