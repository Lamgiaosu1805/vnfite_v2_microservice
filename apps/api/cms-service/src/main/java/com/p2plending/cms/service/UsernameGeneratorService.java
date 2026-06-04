package com.p2plending.cms.service;

import com.p2plending.cms.domain.repository.CmsAdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sinh username từ họ tên tiếng Việt.
 *
 * Quy tắc: lấy tên (từ cuối) + chữ cái đầu các từ còn lại.
 * Ví dụ: "Nghiêm Khắc Lâm" → "lam" + "nk" = "lamnk"
 *         "Nguyễn Văn A"    → "a"   + "nv" = "anv"
 * Nếu trùng: lamnk1, lamnk2, ...
 */
@Service
@RequiredArgsConstructor
public class UsernameGeneratorService {

    private final CmsAdminUserRepository adminRepo;

    @Transactional(readOnly = true)
    public String generate(String fullName) {
        String base = buildBase(fullName);
        List<String> existing = adminRepo.findUsernamesStartingWith(base);
        Set<String> taken = existing.stream().collect(Collectors.toSet());

        if (!taken.contains(base)) return base;

        int index = 1;
        while (taken.contains(base + index)) index++;
        return base + index;
    }

    // "Nghiêm Khắc Lâm" → "lamnk"
    private String buildBase(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 0) return "admin";

        // Tên (từ cuối) — trong tiếng Việt, tên là từ cuối
        String firstName = removeDiacritics(parts[parts.length - 1]);

        // Chữ cái đầu các từ còn lại (họ + đệm), ghép lại
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            String normalized = removeDiacritics(parts[i]);
            if (!normalized.isEmpty()) initials.append(normalized.charAt(0));
        }

        return firstName + initials;
    }

    /** Xoá dấu tiếng Việt, chuyển thành chữ thường, giữ lại a-z0-9 */
    private String removeDiacritics(String input) {
        if (input == null || input.isBlank()) return "";
        // Xử lý đặc biệt cho đ/Đ trước khi NFD decompose
        String s = input.replace("đ", "d").replace("Đ", "d");
        // NFD decompose rồi bỏ combining marks
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        return normalized
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
    }
}
