package com.p2plending.notification.service;

import com.p2plending.notification.client.AuthServiceClient;
import com.p2plending.notification.client.PushNotificationClient;
import com.p2plending.notification.domain.entity.Notification;
import com.p2plending.notification.domain.entity.NotificationCampaign;
import com.p2plending.notification.domain.enums.CampaignSendMode;
import com.p2plending.notification.domain.enums.CampaignStatus;
import com.p2plending.notification.domain.enums.NotificationType;
import com.p2plending.notification.domain.repository.NotificationCampaignRepository;
import com.p2plending.notification.domain.repository.NotificationRepository;
import com.p2plending.notification.dto.NotificationCampaignRequest;
import com.p2plending.notification.dto.NotificationCampaignResponse;
import com.p2plending.notification.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Campaign bắn thông báo marketing — gửi ngay hoặc đặt lịch lặp lại hằng ngày
 * trong một khoảng ngày, vào một giờ cố định. Mỗi lần gửi: ghi lịch sử in-app
 * (bảng notifications) cho từng user trong segment + push FCM đồng thời.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationCampaignService {

    private static final ZoneId TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    private final NotificationCampaignRepository campaignRepository;
    private final NotificationRepository         notificationRepository;
    private final AuthServiceClient              authServiceClient;
    private final PushNotificationClient         pushClient;

    @Transactional
    public NotificationCampaignResponse createCampaign(NotificationCampaignRequest request) {
        if (request.getSendMode() == CampaignSendMode.SCHEDULED) {
            if (request.getScheduledTime() == null || request.getStartDate() == null || request.getEndDate() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Đặt lịch cần đủ giờ gửi, ngày bắt đầu và ngày kết thúc.");
            }
            if (request.getEndDate().isBefore(request.getStartDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ngày kết thúc phải từ ngày bắt đầu trở đi.");
            }
        }

        NotificationCampaign campaign = NotificationCampaign.builder()
                .title(request.getTitle().trim())
                .body(request.getBody().trim())
                .campaignType(request.getCampaignType())
                .segmentKycStatus(request.getSegmentKycStatus())
                .sendMode(request.getSendMode())
                .scheduledTime(request.getSendMode() == CampaignSendMode.SCHEDULED ? request.getScheduledTime() : null)
                .startDate(request.getSendMode() == CampaignSendMode.SCHEDULED ? request.getStartDate() : null)
                .endDate(request.getSendMode() == CampaignSendMode.SCHEDULED ? request.getEndDate() : null)
                .status(CampaignStatus.SCHEDULED)
                .createdBy(request.getCreatedBy())
                .build();
        campaign = campaignRepository.save(campaign);

        if (request.getSendMode() == CampaignSendMode.NOW) {
            LocalDate today = LocalDate.now(TZ);
            executeCampaign(campaign, today);
            campaign.setStatus(CampaignStatus.COMPLETED);
            campaign = campaignRepository.save(campaign);
        }

        return toResponse(campaign);
    }

    /**
     * Chạy 1 lượt gửi cho campaign — fan-out tới toàn bộ user trong segment:
     * ghi Notification (in-app) + push FCM. Không cập nhật lastSentDate khi lỗi,
     * để scheduler tự retry ở lượt chạy tiếp theo (idempotent theo ngày).
     */
    @Transactional
    public void executeCampaign(NotificationCampaign campaign, LocalDate runDate) {
        List<AuthServiceClient.TokenEntry> entries;
        try {
            entries = authServiceClient.getTokensBySegment(campaign.getSegmentKycStatus());
        } catch (Exception ex) {
            log.error("[Campaign] Lỗi lấy danh sách segment cho campaign {}: {}", campaign.getId(), ex.getMessage());
            return;
        }

        if (entries.isEmpty()) {
            log.info("[Campaign] Campaign {} không có người nhận trong segment — bỏ qua lượt {}",
                    campaign.getId(), runDate);
        } else {
            String channel = "MARKETING_" + campaign.getCampaignType();
            for (AuthServiceClient.TokenEntry entry : entries) {
                notificationRepository.save(Notification.builder()
                        .userId(entry.userId())
                        .title(campaign.getTitle())
                        .message(campaign.getBody())
                        .type(NotificationType.PUSH)
                        .channel(channel)
                        .referenceId(campaign.getId())
                        .referenceType("CAMPAIGN")
                        .build());
            }

            List<String> tokens = entries.stream().map(AuthServiceClient.TokenEntry::fcmToken).toList();
            Map<String, Object> data = Map.of("type", channel, "campaignId", campaign.getId());
            pushClient.pushToTokens(tokens, campaign.getTitle(), campaign.getBody(), data);

            log.info("[Campaign] Gửi campaign {} tới {} người dùng (lượt {})",
                    campaign.getId(), entries.size(), runDate);
        }

        campaign.setLastSentDate(runDate);
        campaign.setTotalSentCount(campaign.getTotalSentCount() + entries.size());
        if (campaign.getSendMode() == CampaignSendMode.SCHEDULED
                && campaign.getEndDate() != null
                && !runDate.isBefore(campaign.getEndDate())) {
            campaign.setStatus(CampaignStatus.COMPLETED);
        }
        campaignRepository.save(campaign);
    }

    @Transactional
    public NotificationCampaignResponse cancelCampaign(String id) {
        NotificationCampaign campaign = campaignRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy campaign."));
        if (campaign.getStatus() != CampaignStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chỉ có thể hủy campaign đang ở trạng thái chờ gửi.");
        }
        campaign.setStatus(CampaignStatus.CANCELLED);
        campaignRepository.save(campaign);
        return toResponse(campaign);
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationCampaignResponse> listCampaigns(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = campaignRepository.findByIsDeletedFalseOrderByCreatedAtDesc(pageable);
        return PagedResponse.<NotificationCampaignResponse>builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public NotificationCampaignResponse getCampaign(String id) {
        return campaignRepository.findByIdAndIsDeletedFalse(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy campaign."));
    }

    private NotificationCampaignResponse toResponse(NotificationCampaign c) {
        return NotificationCampaignResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .body(c.getBody())
                .campaignType(c.getCampaignType())
                .segmentKycStatus(c.getSegmentKycStatus())
                .sendMode(c.getSendMode())
                .scheduledTime(c.getScheduledTime())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .status(c.getStatus())
                .lastSentDate(c.getLastSentDate())
                .totalSentCount(c.getTotalSentCount())
                .createdBy(c.getCreatedBy())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
