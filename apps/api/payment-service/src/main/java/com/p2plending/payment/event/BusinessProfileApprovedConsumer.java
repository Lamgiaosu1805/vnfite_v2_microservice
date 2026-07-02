package com.p2plending.payment.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.payment.domain.enums.WalletOwnerType;
import com.p2plending.payment.domain.repository.WalletRepository;
import com.p2plending.payment.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Lắng nghe hồ sơ doanh nghiệp được duyệt để tạo ví + VA riêng cho tư cách doanh nghiệp.
 * Idempotent theo (userId, BUSINESS), nên event retry/replay không tạo trùng ví.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessProfileApprovedConsumer {

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "business-profile.approved", groupId = "${spring.kafka.consumer.group-id}")
    public void onBusinessProfileApproved(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String userId = event.get("userId").asText();
            String businessName = event.path("businessName").asText(null);
            String registrationNumber = event.path("registrationNumber").asText(null);
            String taxCode = event.path("taxCode").asText(null);
            String identityNo = taxCode != null && !taxCode.isBlank() ? taxCode : registrationNumber;

            if (walletRepository.existsByUserIdAndOwnerTypeAndIsDeletedFalse(userId, WalletOwnerType.BUSINESS)) {
                log.debug("business-profile.approved: business wallet đã tồn tại userId={}, bỏ qua", userId);
                return;
            }

            log.info("business-profile.approved: tạo ví doanh nghiệp userId={} businessName={}",
                    userId, businessName);
            walletService.createBusinessWallet(userId, businessName, identityNo);
        } catch (Exception e) {
            // Không throw để tránh retry vô hạn với event lỗi schema/dữ liệu.
            log.error("business-profile.approved consumer error: {} | message={}", e.getMessage(), message, e);
        }
    }
}
