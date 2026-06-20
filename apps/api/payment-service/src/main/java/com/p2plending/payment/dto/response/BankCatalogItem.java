package com.p2plending.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankCatalogItem {
    private String bankCode;
    private String bankName;
    private String bankShortName;
    private String icon;
}
