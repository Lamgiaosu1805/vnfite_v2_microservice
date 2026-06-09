package com.p2plending.loan.dto.response;

import com.p2plending.loan.domain.enums.RepaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
public class RepaymentScheduleResponse {
    private Integer periodNumber;
    private LocalDate dueDate;
    private BigDecimal principalDue;
    private BigDecimal interestDue;
    private BigDecimal totalDue;
    private BigDecimal paidAmount;
    private RepaymentStatus status;
    private Integer dpd;
    private LocalDateTime paidAt;
    /**
     * true = lịch dự kiến tính theo điều khoản đã duyệt, chưa phải lịch thật (khoản chưa FUNDED).
     * false / null = lịch đã được tạo chính thức sau khi khoản FUNDED.
     */
    private Boolean projected;
}
