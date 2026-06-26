package com.p2plending.loan.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Sends OTP through the VNF OTP/Zalo gateway and returns the generated OTP so
 * loan-service can verify it from Redis later.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VnfOtpSenderService {

    private final RestTemplate restTemplate;

    @Value("${app.vnf-otp.url:}")
    private String otpServiceUrl;

    @Value("${app.vnf-otp.path:/common/generate-otp-app-v2}")
    private String otpServicePath;

    @Value("${app.vnf-otp.loan-function-type:7}")
    private int loanFunctionType;

    @Value("${app.vnf-otp.contract-function-type:2}")
    private int contractFunctionType;

    public String sendLoanOtp(String phone) {
        return sendOtp(phone, loanFunctionType);
    }

    public String sendContractOtp(String phone) {
        return sendOtp(phone, contractFunctionType);
    }

    private String sendOtp(String phone, int functionType) {
        if (!StringUtils.hasText(otpServiceUrl)) {
            log.warn("VNF OTP service URL not configured (app.vnf-otp.url) — skipping loan OTP send");
            return null;
        }

        String transactionId = UUID.randomUUID().toString();
        String url = otpServiceUrl + otpServicePath;

        Map<String, Object> body = Map.of(
                "deviceId", "VNFITE_LOAN_SERVICE",
                "sessionId", transactionId,
                "otp", "",
                "functionType", functionType,
                "phoneNumber", phone,
                "identifyNumber", "");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("transactionId", transactionId);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object data = response.getBody().get("data");
                if (data instanceof String otp && StringUtils.hasText(otp)) {
                    log.info("VNF loan OTP sent to phone={} functionType={} txId={}",
                            phone, functionType, transactionId);
                    return otp;
                }
            }
            log.warn("VNF OTP service returned unexpected body for loan phone={}: {}",
                    phone, response.getBody());
        } catch (Exception e) {
            log.error("VNF OTP service call failed for loan phone={} functionType={}: {}",
                    phone, functionType, e.getMessage());
        }
        return null;
    }
}
