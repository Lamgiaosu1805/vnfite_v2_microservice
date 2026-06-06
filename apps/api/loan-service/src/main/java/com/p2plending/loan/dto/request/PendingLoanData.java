package com.p2plending.loan.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Dữ liệu khoản gọi vốn đang chờ xác nhận OTP — lưu tạm trong Redis.
 * Key có namespace, dạng `<env>:loan-service:pending_loan:{borrowerId}`, TTL: 10 phút.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PendingLoanData implements Serializable {

    private String borrowerId;
    private String otp;

    // ── Thông tin khoản vay ──────────────────────────────────────
    private String productCode;
    private BigDecimal amount;
    private Integer termMonths;
    private String purpose;

    // ── Thông tin cá nhân ────────────────────────────────────────
    private BigDecimal monthlyIncome;
    private String occupation;
    private String workplace;
    private String currentAddress;
    private String commune;
    private String province;

    // ── Người tham chiếu 1 ───────────────────────────────────────
    private String ref1FullName;
    private String ref1Relationship;
    private String ref1Phone;
    private String ref1Address;

    // ── Người tham chiếu 2 ───────────────────────────────────────
    private String ref2FullName;
    private String ref2Relationship;
    private String ref2Phone;
    private String ref2Address;
}
