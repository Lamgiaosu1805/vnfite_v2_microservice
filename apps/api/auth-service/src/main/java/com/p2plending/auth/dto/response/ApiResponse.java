package com.p2plending.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private String requestId;
    private LocalDateTime timestamp;
    private int status;
    private String message;
    /** Mã lỗi máy đọc được (ví dụ: DEVICE_CONFLICT) */
    private String errorCode;
    private T data;
    private List<String> errors;

    public static <T> ApiResponse<T> success(String requestId, int status, T data) {
        return ApiResponse.<T>builder()
                .requestId(requestId)
                .timestamp(LocalDateTime.now())
                .status(status)
                .message("Success")
                .data(data)
                .build();
    }

    public static ApiResponse<Void> error(String requestId, int status, String message) {
        return ApiResponse.<Void>builder()
                .requestId(requestId)
                .timestamp(LocalDateTime.now())
                .status(status)
                .message(message)
                .build();
    }

    public static ApiResponse<Void> error(String requestId, int status, String message, List<String> errors) {
        return ApiResponse.<Void>builder()
                .requestId(requestId)
                .timestamp(LocalDateTime.now())
                .status(status)
                .message(message)
                .errors(errors)
                .build();
    }

    public static ApiResponse<Void> error(String requestId, int status, String message, String errorCode) {
        return ApiResponse.<Void>builder()
                .requestId(requestId)
                .timestamp(LocalDateTime.now())
                .status(status)
                .message(message)
                .errorCode(errorCode)
                .build();
    }
}
