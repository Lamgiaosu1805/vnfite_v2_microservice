package com.p2plending.cms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Thẩm định viên nhập kết quả tra cứu CIC bên ngoài (chờ API NĐ94). */
@Data
public class CicLookupRequest {

    @NotNull(message = "Nhóm nợ CIC bắt buộc")
    @Min(value = 1, message = "Nhóm nợ từ 1 đến 5")
    @Max(value = 5, message = "Nhóm nợ từ 1 đến 5")
    private Integer debtGroup;

    @Min(value = 0, message = "Số ngày quá hạn không âm")
    private Integer maxDpd;

    @Min(value = 0, message = "Số tổ chức tín dụng không âm")
    private Integer activeLenders;

    @DecimalMin(value = "0.00", message = "Tổng dư nợ không âm")
    private BigDecimal totalOutstanding;

    @Min(value = 0, message = "Số lần hỏi tin không âm")
    private Integer inquiriesRecent;

    @NotNull(message = "Ngày tra cứu CIC bắt buộc")
    @PastOrPresent(message = "Ngày tra cứu không ở tương lai")
    private LocalDate checkedAt;

    @Size(max = 100)
    private String attachmentFileId;

    @Size(max = 1000)
    private String note;

    /** Bắt buộc tích xác nhận đã có consent tra cứu CIC (NĐ13). */
    @AssertTrue(message = "Phải xác nhận có sự đồng ý tra cứu thông tin tín dụng")
    private boolean consentConfirmed;
}
