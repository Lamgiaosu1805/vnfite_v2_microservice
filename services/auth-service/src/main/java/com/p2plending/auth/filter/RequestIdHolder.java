package com.p2plending.auth.filter;

import org.slf4j.MDC;

public class RequestIdHolder {

    private RequestIdHolder() {}

    public static String get() {
        String id = MDC.get(RequestIdFilter.MDC_KEY);
        return id != null ? id : "N/A";
    }
}
