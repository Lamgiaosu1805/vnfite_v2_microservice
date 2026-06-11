package com.p2plending.loan.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoanDocumentInput {

    @NotBlank(message = "Loại chứng từ là bắt buộc (docType)")
    @Size(max = 100)
    private String docType;

    @NotBlank(message = "fileId là bắt buộc")
    @Size(max = 100)
    private String fileId;

    @Size(max = 255)
    private String fileName;
}
