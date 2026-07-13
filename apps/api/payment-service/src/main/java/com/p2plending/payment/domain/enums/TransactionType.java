package com.p2plending.payment.domain.enums;

public enum TransactionType {
    DEPOSIT,       // Nạp tiền vào ví (MB Bank webhook)
    WITHDRAW,      // Rút tiền ra ngân hàng
    INVEST,        // Khóa tiền khi đặt offer đầu tư
    INVEST_REFUND, // Hoàn tiền khi offer bị từ chối/hủy
    DISBURSEMENT,  // Người gọi vốn nhận tiền giải ngân vào ví VNF (tiền vào)
    DISBURSE_REVERSAL, // Thu hồi tiền đã giải ngân nhầm trước khi có giao dịch phân phối
    INVEST_RESTORE, // Khôi phục và khóa lại tiền đầu tư sau khi hoàn giải ngân
    REPAYMENT,     // Nhà đầu tư nhận tiền gốc+lãi từ khoản cho vay (tiền vào)
    REPAY          // Người gọi vốn trả nợ — trừ khỏi ví (tiền ra)
}
