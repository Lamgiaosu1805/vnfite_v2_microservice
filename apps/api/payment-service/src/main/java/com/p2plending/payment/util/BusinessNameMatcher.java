package com.p2plending.payment.util;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Đối chiếu mềm tên chủ tài khoản ngân hàng (do MB Bank trả về, viết hoa không dấu) với tên
 * doanh nghiệp / hộ kinh doanh trên đăng ký kinh doanh.
 *
 * <p>Tên tài khoản ngân hàng của DN gần như luôn khác ĐKKD một chút: viết hoa không dấu, viết tắt
 * loại hình ("Công ty TNHH" → "CT TNHH"/"CTY TNHH", "Cổ phần" → "CP", "Một thành viên" → "MTV"),
 * thứ tự từ. Nên KHÔNG so khớp tuyệt đối. Cách làm:
 * <ol>
 *   <li>Chuẩn hóa: bỏ dấu, in hoa, bỏ ký tự đặc biệt ({@link TextNormalizer}).</li>
 *   <li>Quy chuẩn cụm loại hình về một token thống nhất (TNHH, CP, MTV, DNTN, HKD...).</li>
 *   <li>Bỏ các token loại hình/"công ty" chung để lấy phần lõi đặc trưng.</li>
 *   <li>Khớp nếu lõi đặc trưng của bên nhỏ hơn nằm trọn trong bên còn lại (subset) — chịu được
 *       đảo thứ tự và thừa từ (vd tên thương mại kèm theo).</li>
 * </ol>
 */
public final class BusinessNameMatcher {

    private BusinessNameMatcher() {}

    /** Cụm loại hình nhiều từ → 1 token thống nhất (áp trên chuỗi đã bỏ dấu, in hoa). Dài trước ngắn. */
    private static final Map<String, String> LEGAL_FORM_PHRASES = new LinkedHashMap<>();
    static {
        LEGAL_FORM_PHRASES.put("TRACH NHIEM HUU HAN", " TNHH ");
        LEGAL_FORM_PHRASES.put("DOANH NGHIEP TU NHAN", " DNTN ");
        LEGAL_FORM_PHRASES.put("HO KINH DOANH", " HKD ");
        LEGAL_FORM_PHRASES.put("MOT THANH VIEN", " MTV ");
        LEGAL_FORM_PHRASES.put("CO PHAN", " CP ");
        LEGAL_FORM_PHRASES.put("CTCP", " CT CP ");
        LEGAL_FORM_PHRASES.put("CTY", " CT ");
        LEGAL_FORM_PHRASES.put("CONG TY", " CT ");
    }

    /** Token chung bị loại để còn lại phần lõi đặc trưng của tên. */
    private static final Set<String> GENERIC_TOKENS = Set.of(
            "CT", "CONG", "TY", "TNHH", "MTV", "CP", "DNTN", "HKD", "HO", "KINH", "DOANH", "NGHIEP", "DN");

    /**
     * @return true nếu {@code bankAccountName} khớp mềm với {@code officialName}
     *         (một trong hai lõi đặc trưng nằm trọn trong lõi còn lại).
     */
    public static boolean matches(String officialName, String bankAccountName) {
        Set<String> a = coreTokens(officialName);
        Set<String> b = coreTokens(bankAccountName);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        Set<String> smaller = a.size() <= b.size() ? a : b;
        Set<String> larger = a.size() <= b.size() ? b : a;
        return larger.containsAll(smaller);
    }

    /** Phần lõi đặc trưng của tên sau khi quy chuẩn loại hình và bỏ token chung. */
    static Set<String> coreTokens(String name) {
        String normalized = TextNormalizer.normalize(name);
        if (normalized.isEmpty()) {
            return Set.of();
        }
        // Bỏ ký tự không phải chữ/số, chèn khoảng đệm hai đầu để match cụm theo ranh giới từ.
        String padded = " " + normalized.replaceAll("[^A-Z0-9]", " ").replaceAll("\\s+", " ").trim() + " ";
        for (Map.Entry<String, String> e : LEGAL_FORM_PHRASES.entrySet()) {
            padded = padded.replace(" " + e.getKey() + " ", e.getValue());
        }
        Set<String> core = new LinkedHashSet<>();
        for (String token : padded.trim().split("\\s+")) {
            if (token.isBlank() || GENERIC_TOKENS.contains(token)) {
                continue;
            }
            core.add(token);
        }
        return core;
    }
}
