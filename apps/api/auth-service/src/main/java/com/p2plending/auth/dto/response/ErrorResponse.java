package com.p2plending.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneId;
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
                .status(status)
                .message(message)
                .timestamp(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                .build();
    }

    public static ErrorResponse of(int status, String message, List<String> details) {
        return ErrorResponse.builder()
                .status(status)
                .message(message)
                .details(details)
                .timestamp(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                .build();
    }
}
