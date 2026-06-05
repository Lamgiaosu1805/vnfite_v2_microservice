package com.p2plending.loan.domain.enums;

/** Kênh ghi nhận giao dịch trả nợ. */
public enum PaymentChannel {
    /** Tiền về qua đối tác thu-chi-hộ (webhook tự động). */
    COLLECTION_PARTNER,
    /** Admin nhập tay trên CMS (giai đoạn chưa tích hợp đối tác). */
    MANUAL_ADMIN
}
