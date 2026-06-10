package com.p2plending.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Payload TIKLUY gửi đến payment-service khi có giao dịch nạp tiền thành công.
 * Map với NotificationInfo1Request của TIKLUY-ms002 (AppFeignService.pushNoti).
 *
 * TIKLUY gọi: POST /notification/save-by-ms-account
 * Header: transactionId (requestId của TIKLUY, dùng làm referenceId để dedup)
 *
 * Lưu ý field naming: TIKLUY dùng camelCase cho accountNo, runningBalance nhưng
 * snake_case cho content_1, content_2 — cần @JsonProperty tường minh.
 */
@Data
public class DepositCallbackRequest {
    /** VNF account number (vd "VNF0000000001") */
    private String accountNo;

    /** Số tiền nạp (string, VND — số nguyên thuần, vd "1000000") */
    private String amount;

    private String title;

    /** "IN" cho nạp tiền, "OUT" cho rút tiền/phí */
    private String category;

    private Integer type;

    /** Số dư tài khoản TIKLUY sau giao dịch */
    private String runningBalance;

    /** Nội dung chuyển khoản dòng 1 — TIKLUY gửi key "content_1" (snake_case) */
    @JsonProperty("content_1")
    private String content1;

    /** Nội dung chuyển khoản dòng 2 — TIKLUY gửi key "content_2" (snake_case) */
    @JsonProperty("content_2")
    private String content2;
}
