package com.p2plending.loan.domain.enums;

/** Trạng thái của một kỳ trả nợ. */
public enum RepaymentStatus {
    PENDING,  // chưa đến hạn hoặc chưa trả
    PARTIAL,  // đã trả một phần
    PAID,     // đã trả đủ
    OVERDUE   // quá hạn mà chưa trả đủ
}
