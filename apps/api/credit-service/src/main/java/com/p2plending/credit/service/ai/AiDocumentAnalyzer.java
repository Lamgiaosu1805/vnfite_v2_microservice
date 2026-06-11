package com.p2plending.credit.service.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * AI phân tích chứng từ tài chính/thu nhập (sao kê lương, sao kê ngân hàng,
 * hóa đơn, sổ bán hàng, HĐLĐ, ĐKKD...).
 *
 * GIỚI HẠN QUAN TRỌNG: AI không thể khẳng định 100% một chứng từ là giả mạo —
 * chỉnh sửa tinh vi vẫn có thể qua mặt. Kết quả là CẢNH BÁO mức độ tin cậy
 * (kiểm tra số học nội tại, đối chiếu khai báo, bất thường định dạng)
 * để hỗ trợ admin thẩm định — không phải phán quyết.
 */
public interface AiDocumentAnalyzer {

    /**
     * @return kết quả phân tích, hoặc null nếu AI tắt
     */
    DocumentCheckResult analyze(String mimeType, String fileBase64, String context);

    /** Schema được Claude SDK tự derive từ record này (structured outputs) */
    record DocumentCheckResult(
            @JsonPropertyDescription("Loại chứng từ AI nhận diện được từ nội dung (vd: sao kê ngân hàng MB, hợp đồng lao động)")
            String docTypeDetected,

            @JsonPropertyDescription("CONSISTENT (nhất quán, không thấy bất thường) | SUSPICIOUS (có điểm đáng ngờ cần kiểm tra) | HIGH_RISK (nhiều dấu hiệu bất thường nghiêm trọng) | UNREADABLE (không đọc được)")
            String verdict,

            @JsonPropertyDescription("Độ tin cậy của chứng từ 0-100 (100 = hoàn toàn nhất quán)")
            Integer trustScore,

            @JsonPropertyDescription("Tên chủ tài khoản/người lao động trích xuất từ chứng từ")
            String ownerName,

            @JsonPropertyDescription("Tên ngân hàng/công ty/tổ chức phát hành chứng từ")
            String organizationName,

            @JsonPropertyDescription("Thu nhập hàng tháng trích xuất được (VND, chỉ số, vd 15000000). Để trống nếu không xác định được")
            String extractedMonthlyIncome,

            @JsonPropertyDescription("Các phát hiện khi kiểm tra tính nhất quán nội tại: số dư chạy có khớp không, font/căn lề, định dạng ngày, con dấu...")
            List<String> findings,

            @JsonPropertyDescription("Các điểm KHÔNG KHỚP giữa chứng từ và thông tin khai báo (tên, thu nhập, nơi làm việc). Mảng rỗng nếu khớp hết")
            List<String> consistencyIssues,

            @JsonPropertyDescription("Tóm tắt 2-3 câu cho admin thẩm định, tiếng Việt")
            String summary
    ) {}
}
