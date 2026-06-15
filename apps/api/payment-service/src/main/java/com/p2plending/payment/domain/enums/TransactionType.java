package com.p2plending.payment.domain.enums;

public enum TransactionType {
    DEPOSIT,       // Nạp tiền vào ví (MB Bank webhook)
    WITHDRAW,      // Rút tiền ra ngân hàng
    INVEST,        // Khóa tiền khi đặt offer đầu tư
    INVEST_REFUND, // Hoàn tiền khi offer bị từ chối/hủy
    REPAYMENT,     // Nhà đầu tư nhận tiền gốc+lãi từ khoản cho vay (tiền vào)
    REPAY          // Người gọi vốn trả nợ — trừ khỏi ví (tiền ra)
}
