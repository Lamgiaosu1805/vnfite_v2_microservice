package com.p2plending.credit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Yêu cầu AI phân tích một chứng từ thu nhập.
 *
 * Hai cách truyền file (chọn 1):
 *  1. fileId — đã upload lên file-manager (service.vnfite.com.vn), credit-service tự fetch. ƯU TIÊN.
 *  2. fileBase64 + mimeType — gửi trực tiếp (cho test, hoặc khi không qua file-manager).
 */
@Data
public class AnalyzeDocumentRequest {

    @NotBlank(message = "userId không được để trống")
    private String userId;

    private Long loanRequestId;

    /** SALARY_STATEMENT | BANK_STATEMENT | LABOR_CONTRACT | BUSINESS_LICENSE | OTHER */
    @NotBlank(message = "docType không được để trống")
    private String docType;

    private String fileName;

    /** ID file đã upload lên file-manager — cách truyền ưu tiên */
    private String fileId;

    /** image/jpeg | image/png | image/webp | image/gif | application/pdf — chỉ cần khi dùng fileBase64 */
    private String mimeType;

    /** Base64 của file — chỉ cần khi không dùng fileId */
    private String fileBase64;

    // ── Thông tin khai báo để AI đối chiếu ──
    private String declaredFullName;
    private BigDecimal declaredMonthlyIncome;
    private String declaredOccupation;
    private String declaredWorkplace;
}
