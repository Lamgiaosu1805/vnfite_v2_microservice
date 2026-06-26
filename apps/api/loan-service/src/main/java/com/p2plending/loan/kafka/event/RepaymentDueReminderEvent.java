package com.p2plending.loan.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Phát khi tới ngày đến hạn nhưng ví người gọi vốn không đủ số dư để auto-debit (kể cả một phần).
 * notification-service consume để nhắc người gọi vốn nạp tiền. dpd=0 nghĩa là đến hạn hôm nay,
 * dpd&gt;0 nghĩa là đã quá hạn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepaymentDueReminderEvent {
    private String loanId;
    private String loanCode;
    private String borrowerId;
    private Integer periodNumber;
    private Integer totalPeriods;
    private BigDecimal amountDue;
    private LocalDate dueDate;
    /** >0: nhắc trước hạn; 0/null: đến hạn hôm nay hoặc event cũ. */
    private Integer daysUntilDue;
    private int dpd;
}
