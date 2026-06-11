package com.p2plending.credit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Yêu cầu chấm điểm — CMS (hoặc loan-service) gọi khi thẩm định khoản gọi vốn.
 *
 * Caller truyền các dữ liệu nó đã có từ auth-service/loan-service
 * (dateOfBirth từ kyc_submissions, accountCreatedAt từ users...).
 * Dữ liệu tài chính tự khai lấy từ borrower_profiles trong credit_db.
 * Field nào thiếu → tiêu chí tương ứng 0 điểm + flag missingData.
 */
@Data
public class EvaluateScoreRequest {

    @NotBlank(message = "userId không được để trống")
    private String userId;

    // ── Thông tin khoản gọi vốn (optional — null nếu pre-score) ──
    private String loanRequestId;
    private BigDecimal loanAmount;
    private Integer termMonths;
    private String purpose;

    // ── Dữ liệu từ auth-service (caller cung cấp) ──
    private LocalDate dateOfBirth;
    private String kycStatus;
    private Boolean hasReferrer;
    private LocalDateTime accountCreatedAt;

    // ── Dữ liệu từ loan-service (caller cung cấp) ──
    private Integer completedLoanCount;

    /**
     * Thu nhập + nghề nghiệp lấy từ chính đơn gọi vốn (loan_requests.monthly_income / occupation).
     * Ưu tiên hơn borrower_profiles nếu cả hai cùng có.
     * occupation nhận OccupationCategory của loan-service (CIVIL_SERVANT, OFFICE_STAFF...)
     * hoặc nhóm scorecard (GOV_EMPLOYEE, SALARIED...) — credit-service tự map.
     */
    private BigDecimal monthlyIncome;
    private String occupation;
    private BigDecimal existingMonthlyDebt;
}
