package com.p2plending.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Trả về khi nhà đầu tư đặt lệnh đầu tư: offer được tạo ở trạng thái PENDING +
 * một hợp đồng đầu tư PENDING_SIGNATURE chờ ký OTP để hoàn tất.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OfferCreateResponse {
    private String offerId;
    private ContractResponse contract;
}
