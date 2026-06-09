package com.p2plending.loan.dto.request;

import lombok.Data;

/** Body khi OPS bấm giải ngân trên CMS. */
@Data
public class DisburseRequest {
    /** Username OPS thực hiện giải ngân (để lưu vào loan_requests.disbursed_by). */
    private String disbursedBy;
}
