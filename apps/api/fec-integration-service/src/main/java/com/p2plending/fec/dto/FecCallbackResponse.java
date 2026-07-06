package com.p2plending.fec.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FecCallbackResponse {
    private int code;
    private Object data;
    private String message;
}
