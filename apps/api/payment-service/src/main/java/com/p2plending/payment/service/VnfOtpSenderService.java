package com.p2plending.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.p2plending.payment.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Gọi VNF OTP service (tại app.vnf-otp.url) để gửi OTP SMS và lấy mã OTP lưu vào Redis.
 * Mirror pattern từ auth-service VnfOtpSenderService; function type 2 = thao tác bảo mật.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VnfOtpSenderService {

    private final RestTemplate restTemplate;
    private final AppProperties appProperties;

    /**
     * Gửi OTP qua VNF OTP service.
     *
     * @param phone        số điện thoại nhận OTP
     * @param functionType loại tính năng (2 = change-password / withdrawal)
     * @return mã OTP đã được gửi (để lưu Redis), hoặc null nếu lỗi
     */
    public String sendOtp(String phone, int functionType) {
        String baseUrl = appProperties.getVnfOtp().getUrl();
        if (!StringUtils.hasText(baseUrl)) {
            log.warn("vnfOtp.url chưa được cấu hình — không gửi OTP SMS");
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "phone", phone,
                    "functionType", functionType);

            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    baseUrl + "/common/generate-otp-app-v2",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class);

            JsonNode respBody = resp.getBody();
            if (respBody == null) {
                log.error("VNF OTP service trả body null cho phone={}", phone);
                return null;
            }
            JsonNode dataNode = respBody.path("data");
            if (dataNode.isNull() || dataNode.isMissingNode()) {
                log.error("VNF OTP service trả data null cho phone={} body={}", phone, respBody);
                return null;
            }
            String otp = dataNode.asText(null);
            if (!StringUtils.hasText(otp)) {
                log.error("VNF OTP service trả otp rỗng cho phone={}", phone);
                return null;
            }
            log.info("vnf.otp.sent phone={} functionType={}", phone, functionType);
            return otp;

        } catch (Exception e) {
            log.error("vnf.otp.error phone={} functionType={}: {}", phone, functionType, e.getMessage());
            return null;
        }
    }
}
