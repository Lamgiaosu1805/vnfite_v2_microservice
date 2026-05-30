package com.p2plending.auth.dto.request;

import com.p2plending.auth.domain.enums.DocType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KycSubmitRequest {

    @NotNull(message = "Document type is required")
    private DocType docType;

    @NotBlank(message = "Document URL is required")
    @Size(max = 500, message = "Document URL must not exceed 500 characters")
    private String docUrl;
}
