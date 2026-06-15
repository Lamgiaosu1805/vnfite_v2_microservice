package com.p2plending.loan.domain.enums;

/** Trạng thái hàng đợi đối soát cộng tiền hoàn trả cho nhà đầu tư. */
public enum PendingCreditStatus {
    /** Cộng ví lần đầu thất bại, đang chờ job đối soát thử lại. */
    PENDING,
    /** Đã cộng ví thành công (qua đối soát). */
    COMPLETED
}
