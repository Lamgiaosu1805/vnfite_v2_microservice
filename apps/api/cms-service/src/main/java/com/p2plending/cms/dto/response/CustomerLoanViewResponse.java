package com.p2plending.cms.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dữ liệu khoản gọi vốn chỉ-đọc dành cho kinh doanh/chăm sóc khách hàng.
 * Không bao gồm thông tin thẩm định, chứng từ, tham chiếu hoặc dữ liệu nhà đầu tư.
 */
public record CustomerLoanViewResponse(
        String loanId,
        String loanCode,
        String productName,
        String productCategory,
        String businessName,
        BigDecimal amount,
        BigDecimal fundedAmount,
        int confirmedInvestorCount,
        BigDecimal interestRate,
        Integer termMonths,
        String purpose,
        String status,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {}
