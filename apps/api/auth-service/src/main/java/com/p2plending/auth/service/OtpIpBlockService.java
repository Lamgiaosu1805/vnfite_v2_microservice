package com.p2plending.auth.service;

import com.p2plending.auth.domain.entity.OtpIpBlock;
import com.p2plending.auth.domain.entity.OtpIpUnblockRequest;
import com.p2plending.auth.domain.repository.OtpIpBlockRepository;
import com.p2plending.auth.domain.repository.OtpIpUnblockRequestRepository;
import com.p2plending.auth.dto.response.PagedResponse;
import com.p2plending.auth.exception.OtpIpBlockedException;
import com.p2plending.auth.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OtpIpBlockService {
    private static final String PENDING = "PENDING";
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final OtpIpBlockRepository blockRepository;
    private final OtpIpUnblockRequestRepository requestRepository;

    @Transactional(readOnly = true)
    public void assertRegistrationAllowed(String clientIp) {
        blockRepository.findByIpAddressAndIsDeletedFalse(normalizeIp(clientIp))
                .filter(OtpIpBlock::isActive)
                .ifPresent(block -> { throw new OtpIpBlockedException(); });
    }

    @Transactional
    public void blockAutomatically(String clientIp, String reason) {
        String ip = normalizeIp(clientIp);
        OtpIpBlock block = blockRepository.findByIpAddressAndIsDeletedFalse(ip).orElse(null);
        if (block == null) {
            block = OtpIpBlock.builder()
                    .id(UUID.randomUUID().toString())
                    .ipAddress(ip)
                    .active(true)
                    .reason(reason)
                    .blockedBy("SYSTEM_SECURITY")
                    .build();
        } else {
            block.setActive(true);
            block.setReason(reason);
            block.setBlockedBy("SYSTEM_SECURITY");
            block.setUnblockedBy(null);
            block.setUnblockedAt(null);
            block.setDeleted(false);
        }
        blockRepository.save(block);
    }

    @Transactional
    public Map<String, String> createUnblockRequest(String phone, String note, String clientIp) {
        String ip = normalizeIp(clientIp);
        OtpIpBlock block = blockRepository.findByIpAddressAndIsDeletedFalse(ip).orElse(null);
        if (block == null || !block.isActive()) {
            return Map.of("message", "Mạng hiện không bị chặn. Vui lòng thử đăng ký lại.");
        }
        String normalizedPhone = phone.trim();
        if (requestRepository.findFirstByIpAddressAndPhoneAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(ip, normalizedPhone, PENDING).isPresent()) {
            return Map.of("message", "Yêu cầu hỗ trợ của bạn đang được xử lý.");
        }
        requestRepository.save(OtpIpUnblockRequest.builder()
                .id(UUID.randomUUID().toString()).ipAddress(ip).phone(normalizedPhone).status(PENDING)
                .requesterNote(StringUtils.hasText(note) ? note.trim() : null).build());
        return Map.of("message", "Đã gửi yêu cầu hỗ trợ. VNFITE sẽ kiểm tra và phản hồi sớm nhất có thể.");
    }

    @Transactional(readOnly = true)
    public PagedResponse<OtpIpUnblockRequest> listRequests(String status, int page, int size) {
        return PagedResponse.from(requestRepository.findByStatusAndIsDeletedFalseOrderByCreatedAtDesc(
                StringUtils.hasText(status) ? status.trim().toUpperCase() : PENDING, PageRequest.of(page, size)));
    }

    @Transactional
    public OtpIpUnblockRequest reviewRequest(String requestId, boolean approved, String note, String reviewedBy) {
        OtpIpUnblockRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu mở chặn"));
        if (!PENDING.equals(request.getStatus())) throw new IllegalArgumentException("Yêu cầu này đã được xử lý");
        request.setStatus(approved ? "APPROVED" : "REJECTED");
        request.setReviewNote(StringUtils.hasText(note) ? note.trim() : null);
        request.setReviewedBy(reviewedBy);
        request.setReviewedAt(LocalDateTime.now(VIETNAM_ZONE));
        if (approved) {
            blockRepository.findByIpAddressAndIsDeletedFalse(request.getIpAddress()).ifPresent(block -> {
                block.setActive(false);
                block.setUnblockedBy(reviewedBy);
                block.setUnblockedAt(LocalDateTime.now(VIETNAM_ZONE));
            });
        }
        return request;
    }

    private String normalizeIp(String ip) {
        String normalized = ip == null ? "unknown" : ip.trim();
        return normalized.matches("[0-9a-fA-F:.]{1,64}") ? normalized : "unknown";
    }
}
