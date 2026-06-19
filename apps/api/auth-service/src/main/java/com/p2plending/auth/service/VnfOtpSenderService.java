package com.p2plending.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Gọi VNF App Management Service để gửi OTP qua ZNS/SMS và lấy về mã OTP thật.
 * Chỉ active khi app.otp.mock=false (live).
 */
@Service
@Slf4j
public class VnfOtpSenderService {

    // functionType mapping theo hệ thống cũ
    public static final int FN_REGISTER        = 7;  // CREATE_ACCOUNT
    public static final int FN_FORGOT_PASSWORD = 6;  // FORGOT_PASSWORD
    public static final int FN_CHANGE_PASSWORD = 2;  // CHANGE_PASSWORD
    public static final int FN_KYC             = 7;  // dùng chung CREATE_ACCOUNT
    public static final int FN_BIOMETRIC       = 2;  // dùng chung CHANGE_PASSWORD
    public static final int FN_DEVICE_RESET    = 1;  // RESET_PASSWORD

    @Value("${app.vnf-otp.url:}")
    private String otpServiceUrl;

    @Value("${app.vnf-otp.path:/common/generate-otp-app-v2}")
    private String otpServicePath;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Gửi OTP qua VNF service.
     * @return OTP string nếu thành công, null nếu lỗi (fallback: OTP vẫn trả về response).
     */
    public String sendOtp(String phone, int functionType) {
        if (otpServiceUrl == null || otpServiceUrl.isBlank()) {
            log.warn("VNF OTP service URL not configured (app.vnf-otp.url) — skipping SMS send");
            return null;
        }

        String transactionId = UUID.randomUUID().toString();
        String url = otpServiceUrl + otpServicePath;

        Map<String, Object> body = Map.of(
            "deviceId",     "VNFITE_AUTH_SERVICE",
            "sessionId",    transactionId,
            "otp",          "",
            "functionType", functionType,
            "phoneNumber",  phone,
            "identifyNumber", ""
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("transactionId", transactionId);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object data = response.getBody().get("data");
                if (data instanceof String otp && !otp.isBlank()) {
                    log.info("VNF OTP sent to phone={} functionType={} txId={}", phone, functionType, transactionId);
                    return otp;
                }
            }
            log.warn("VNF OTP service returned unexpected body for phone={}: {}", phone, response.getBody());
        } catch (Exception e) {
            log.error("VNF OTP service call failed for phone={}: {}", phone, e.getMessage());
        }
        return null;
    }
}
