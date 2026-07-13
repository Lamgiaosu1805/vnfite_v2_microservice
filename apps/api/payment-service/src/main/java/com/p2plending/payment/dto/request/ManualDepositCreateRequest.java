package com.p2plending.payment.dto.request;

import com.p2plending.payment.domain.enums.WalletOwnerType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ManualDepositCreateRequest {
    @NotNull
    @DecimalMin(value = "1000", message = "Số tiền nạp tối thiểu là 1.000 VNĐ")
    private BigDecimal amount;

    private WalletOwnerType ownerType = WalletOwnerType.PERSONAL;

    @NotBlank
    private String billFileId;

    @NotBlank
    private String billFileName;
}
