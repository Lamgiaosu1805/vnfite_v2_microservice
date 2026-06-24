package com.p2plending.payment.service;

import com.p2plending.payment.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Gọi VNF OTP service để gửi OTP SMS và lấy mã OTP lưu vào Redis.
 * Dùng cùng request format với auth-service để tương thích VNF App Management API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VnfOtpSenderService {

    // functionType mapping theo hệ thống cũ — dùng chung với auth-service
    public static final int FN_WITHDRAWAL = 2; // CHANGE_PASSWORD / security operation

    private final RestTemplate restTemplate;
    private final AppProperties appProperties;

    /**
     * Gửi OTP qua VNF OTP service.
     *
     * @param phone        số điện thoại nhận OTP
     * @param functionType loại tính năng
     * @return mã OTP đã được gửi (để lưu Redis), hoặc null nếu lỗi
     */
    public String sendOtp(String phone, int functionType) {
        String baseUrl = appProperties.getVnfOtp().getUrl();
        if (!StringUtils.hasText(baseUrl)) {
            log.warn("vnfOtp.url chưa được cấu hình — không gửi OTP SMS");
            return null;
        }
        String transactionId = UUID.randomUUID().toString();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("transactionId", transactionId);

            Map<String, Object> body = Map.of(
                    "deviceId",       "VNFITE_PAYMENT_SERVICE",
                    "sessionId",      transactionId,
                    "otp",            "",
                    "functionType",   functionType,
                    "phoneNumber",    phone,
                    "identifyNumber", "");

            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/common/generate-otp-app-v2",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object data = resp.getBody().get("data");
                if (data instanceof String otp && StringUtils.hasText(otp)) {
                    log.info("vnf.otp.sent phone={} functionType={} txId={}", phone, functionType, transactionId);
                    return otp;
                }
            }
            log.error("VNF OTP service trả kết quả không hợp lệ cho phone={} body={}", phone, resp.getBody());
            return null;

        } catch (Exception e) {
            log.error("vnf.otp.error phone={} functionType={}: {}", phone, functionType, e.getMessage());
            return null;
        }
    }
}
