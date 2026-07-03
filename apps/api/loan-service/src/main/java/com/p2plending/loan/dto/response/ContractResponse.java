package com.p2plending.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.p2plending.loan.domain.enums.ContractStatus;
import com.p2plending.loan.domain.enums.ContractType;
import com.p2plending.loan.domain.enums.LoanStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContractResponse {
    private String id;
    private String loanId;
    private String loanCode;
    /** Trạng thái hiện tại của khoản — để frontend điều hướng sau khi ký. */
    private LoanStatus loanStatus;
    private ContractType contractType;
    private String partyId;
    /** Tư cách Bên ký: PERSONAL | BUSINESS (null = cá nhân, tương thích hợp đồng cũ). */
    private String partyType;
    /** Tên pháp nhân (khoản DN); null với cá nhân. */
    private String partyName;
    /** Số ĐKKD/MST với khoản DN. */
    private String partyIdentityNo;
    /** Người đại diện pháp luật của DN. */
    private String partyRepresentative;
    private String offerId;
    private String contractNo;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private Integer termMonths;
    private String provider;
    private String documentUrl;
    private String signedDocumentUrl;
    private ContractStatus status;
    private LocalDateTime issuedAt;
    private LocalDateTime signedAt;
    private String signedVia;
    private LocalDateTime createdAt;
}
