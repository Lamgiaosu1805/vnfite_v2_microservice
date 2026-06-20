package com.p2plending.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Một mục ngân hàng từ TIKLUY GET common/bank.
 * Chỉ giữ các field mobile cần — bỏ qua các field thừa bằng @JsonIgnoreProperties.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BankCatalogItem {
    private String bankCode;
    private String bankName;
    private String bankShortName;
    private String icon;
}
