package com.p2plending.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Trả về khi nhà đầu tư xác nhận đầu tư. Lệnh được chấp nhận ngay theo luồng vận hành hiện hành.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OfferCreateResponse {
    private String offerId;
    private ContractResponse contract;
}
