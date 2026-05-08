package com.p2plending.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private int status;
    private String message;
    private List<String> details;
    private LocalDateTime timestamp;

    public static ErrorResponse of(int status, String message) {
        return ErrorResponse.builder()
                .status(status).message(message).timestamp(LocalDateTime.now()).build();
    }

    public static ErrorResponse of(int status, String message, List<String> details) {
        return ErrorResponse.builder()
                .status(status).message(message).details(details).timestamp(LocalDateTime.now()).build();
    }
}
