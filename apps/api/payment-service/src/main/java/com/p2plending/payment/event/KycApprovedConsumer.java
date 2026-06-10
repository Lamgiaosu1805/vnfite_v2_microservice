package com.p2plending.payment.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.payment.domain.repository.WalletRepository;
import com.p2plending.payment.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Lắng nghe event kyc.approved từ auth-service.
 * Tự động tạo ví + VA TIKLUY cho user vừa được duyệt KYC.
 *
 * Đây là fallback an toàn: CMS đã gọi /internal/payment/wallet/init (sync) trước,
 * nhưng nếu payment-service chưa sẵn sàng lúc đó, consumer này sẽ xử lý lại.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KycApprovedConsumer {

    private final WalletService    walletService;
    private final WalletRepository walletRepository;
    private final ObjectMapper     objectMapper;

    @KafkaListener(topics = "kyc.approved", groupId = "${spring.kafka.consumer.group-id}")
    public void onKycApproved(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String userId      = event.get("userId").asText();
            String fullName    = event.path("fullName").asText(null);
            String cccdNumber  = event.path("cccdNumber").asText(null);

            // Idempotent: bỏ qua nếu ví đã được tạo (vd sync call từ CMS đã tạo rồi)
            if (walletRepository.existsByUserId(userId)) {
                log.debug("kyc.approved: wallet đã tồn tại cho userId={}, bỏ qua", userId);
                return;
            }

            log.info("kyc.approved: tạo ví cho userId={}", userId);
            walletService.createWallet(userId, fullName, cccdNumber);

        } catch (Exception e) {
            // Không throw → Kafka không retry vô tận với non-retryable error
            // Log đầy đủ để trace
            log.error("kyc.approved consumer error: {} | message={}", e.getMessage(), message, e);
        }
    }
}
