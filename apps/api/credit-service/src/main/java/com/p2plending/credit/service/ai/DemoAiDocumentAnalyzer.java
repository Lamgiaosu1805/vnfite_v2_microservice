package com.p2plending.credit.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Demo document analyzer — trả về kết quả CONSISTENT với trustScore cao
 * và ghi rõ "chế độ demo" trong summary. Không đọc file thật.
 *
 * Bật bằng: APP_AI_ENABLED=true + APP_AI_MODE=demo
 */
@Service
@ConditionalOnExpression("'${app.ai.enabled:false}'.equals('true') and '${app.ai.mode:claude}'.equals('demo')")
@Slf4j
public class DemoAiDocumentAnalyzer implements AiDocumentAnalyzer {

    public DemoAiDocumentAnalyzer() {
        log.info("DemoAiDocumentAnalyzer bật — không gọi AI API (chỉ dùng để test UI)");
    }

    @Override
    public DocumentCheckResult analyze(String mimeType, String fileBase64, String context) {
        log.debug("[DEMO] Document analysis — mimeType={} contextLen={}", mimeType, context.length());

        String docTypeHint = extractDocTypeHint(context);
        String declaredName = extractDeclaredName(context);
        String declaredIncome = extractDeclaredIncome(context);

        List<String> findings = new ArrayList<>();
        findings.add("Định dạng file hợp lệ (" + mimeType + ")");
        findings.add("Kiểm tra số học nội tại: không phát hiện sai lệch (chế độ demo)");
        findings.add("Font chữ và bố cục: đồng nhất");

        List<String> consistencyIssues = new ArrayList<>();
        // Demo không báo lỗi — chứng từ luôn nhất quán trong chế độ test

        String summary = "[CHẾ ĐỘ DEMO — không phân tích file thật] " +
                "Chứng từ loại " + docTypeHint + " đã được nhận." +
                (declaredName != null ? " Họ tên khai báo: " + declaredName + "." : "") +
                (declaredIncome != null ? " Thu nhập khai báo: " + declaredIncome + " VND/tháng." : "") +
                " Khi bật APP_AI_MODE=claude với ANTHROPIC_API_KEY, Claude sẽ phân tích nội dung file thật.";

        return new DocumentCheckResult(
                docTypeHint,
                "CONSISTENT",
                85,
                declaredName,
                null,
                declaredIncome,
                findings,
                consistencyIssues,
                summary
        );
    }

    private String extractDocTypeHint(String context) {
        if (context.contains("SALARY_STATEMENT") || context.contains("sao kê lương")) return "Sao kê lương";
        if (context.contains("BANK_STATEMENT"))   return "Sao kê ngân hàng";
        if (context.contains("BUSINESS_LICENSE")) return "Giấy phép kinh doanh";
        if (context.contains("TAX_RETURN"))       return "Tờ khai thuế";
        if (context.contains("EMPLOYMENT_CONTRACT")) return "Hợp đồng lao động";
        return "Chứng từ thu nhập";
    }

    private String extractDeclaredName(String context) {
        int idx = context.indexOf("Họ tên:");
        if (idx < 0) idx = context.indexOf("declaredFullName");
        if (idx < 0) return null;
        String rest = context.substring(idx).lines().findFirst().orElse("");
        String[] parts = rest.split(":", 2);
        if (parts.length < 2) return null;
        String name = parts[1].trim().split("[,\n]")[0].trim();
        return name.isEmpty() || name.equals("(không khai)") ? null : name;
    }

    private String extractDeclaredIncome(String context) {
        int idx = context.indexOf("Thu nhập hàng tháng:");
        if (idx < 0) idx = context.indexOf("declaredMonthlyIncome");
        if (idx < 0) return null;
        String rest = context.substring(idx).lines().findFirst().orElse("");
        String[] parts = rest.split(":", 2);
        if (parts.length < 2) return null;
        String val = parts[1].trim().split("[,\n VND]")[0].trim();
        return val.isEmpty() || val.equals("(không khai)") ? null : val;
    }
}
