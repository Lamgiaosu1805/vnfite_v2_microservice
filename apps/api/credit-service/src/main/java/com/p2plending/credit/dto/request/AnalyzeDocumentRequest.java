package com.p2plending.credit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Yêu cầu AI phân tích một chứng từ thu nhập.
 * File truyền dạng base64 — ảnh (jpeg/png/webp, tối đa ~5MB) hoặc PDF (tối đa ~30MB).
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

    /** image/jpeg | image/png | image/webp | image/gif | application/pdf */
    @NotBlank(message = "mimeType không được để trống")
    private String mimeType;

    @NotBlank(message = "fileBase64 không được để trống")
    private String fileBase64;

    // ── Thông tin khai báo để AI đối chiếu ──
    private String declaredFullName;
    private BigDecimal declaredMonthlyIncome;
    private String declaredOccupation;
    private String declaredWorkplace;
}
