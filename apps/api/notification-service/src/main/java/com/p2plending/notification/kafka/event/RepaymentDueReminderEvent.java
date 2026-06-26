package com.p2plending.notification.kafka.event;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Đến hạn nhưng ví người gọi vốn không đủ — nhắc nạp tiền. dpd=0: đến hạn hôm nay; dpd>0: đã quá hạn. */
@Data
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
