package com.p2plending.fec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FecLeadSubmitRequest {
    private String transId;

    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    private String phoneNumber;

    @NotBlank(message = "CCCD không được để trống")
    @Pattern(regexp = "\\d{12}", message = "CCCD phải gồm 12 chữ số")
    private String nid;

    private String dob;
    private String email;

    @NotNull(message = "Số tiền đăng ký không được để trống")
    private Long loanAmount;

    @NotNull(message = "Kỳ hạn không được để trống")
    private Integer tenor;

    private String leadSource;
    private String agentCode;

    @NotBlank(message = "Consent type không được để trống")
    private String consentType;

    @NotBlank(message = "Consent tickbox không được để trống")
    private String consentTickbox;

    @NotBlank(message = "Consent content không được để trống")
    private String consentContent;
}
