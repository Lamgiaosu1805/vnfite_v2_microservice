package com.p2plending.notification.service;

import com.p2plending.notification.client.AuthServiceClient;
import com.p2plending.notification.client.PushNotificationClient;
import com.p2plending.notification.domain.entity.NotificationCampaign;
import com.p2plending.notification.domain.enums.CampaignSendMode;
import com.p2plending.notification.domain.enums.CampaignStatus;
import com.p2plending.notification.domain.enums.CampaignType;
import com.p2plending.notification.domain.repository.NotificationCampaignRepository;
import com.p2plending.notification.domain.repository.NotificationRepository;
import com.p2plending.notification.dto.NotificationCampaignRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCampaignServiceTest {

    @Mock private NotificationCampaignRepository campaignRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private AuthServiceClient authServiceClient;
    @Mock private PushNotificationClient pushClient;
    @InjectMocks private NotificationCampaignService campaignService;

    @Test
    void createCampaign_now_sendsImmediatelyAndMarksCompleted() {
        when(campaignRepository.save(any(NotificationCampaign.class)))
                .thenAnswer(i -> {
                    NotificationCampaign c = i.getArgument(0);
                    if (c.getId() == null) c.setId("test-campaign-id");
                    return c;
                });
        when(authServiceClient.getTokensBySegment(any()))
                .thenReturn(List.of(new AuthServiceClient.TokenEntry("user-1", "token-1")));

        NotificationCampaignRequest request = new NotificationCampaignRequest();
        request.setTitle("Khuyến mại tháng 7");
        request.setBody("Nội dung khuyến mại");
        request.setCampaignType(CampaignType.PROMOTION);
        request.setSendMode(CampaignSendMode.NOW);

        var response = campaignService.createCampaign(request);

        assertThat(response.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
        assertThat(response.getTotalSentCount()).isEqualTo(1);
        assertThat(response.getLastSentDate()).isEqualTo(LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    @Test
    void createCampaign_scheduled_doesNotSendImmediately() {
        when(campaignRepository.save(any(NotificationCampaign.class)))
                .thenAnswer(i -> i.getArgument(0));

        NotificationCampaignRequest request = new NotificationCampaignRequest();
        request.setTitle("Thông báo hệ thống");
        request.setBody("Nội dung hệ thống");
        request.setCampaignType(CampaignType.SYSTEM);
        request.setSendMode(CampaignSendMode.SCHEDULED);
        request.setScheduledTime(LocalTime.of(9, 0));
        request.setStartDate(LocalDate.of(2026, 7, 20));
        request.setEndDate(LocalDate.of(2026, 7, 27));

        var response = campaignService.createCampaign(request);

        assertThat(response.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
        assertThat(response.getLastSentDate()).isNull();
        assertThat(response.getTotalSentCount()).isZero();
        // sendMode=SCHEDULED không gửi ngay lúc tạo — không được gọi tới segment/push.
        org.mockito.Mockito.verifyNoInteractions(authServiceClient, pushClient);
    }

    @Test
    void createCampaign_scheduledMissingFields_throws() {
        NotificationCampaignRequest request = new NotificationCampaignRequest();
        request.setTitle("Thiếu lịch");
        request.setBody("Nội dung");
        request.setCampaignType(CampaignType.SYSTEM);
        request.setSendMode(CampaignSendMode.SCHEDULED);
        // thiếu scheduledTime/startDate/endDate

        assertThatThrownBy(() -> campaignService.createCampaign(request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void executeCampaign_lastDay_marksCompleted() {
        NotificationCampaign campaign = NotificationCampaign.builder()
                .id("campaign-1")
                .title("t").body("b")
                .campaignType(CampaignType.PROMOTION)
                .sendMode(CampaignSendMode.SCHEDULED)
                .startDate(LocalDate.of(2026, 7, 20))
                .endDate(LocalDate.of(2026, 7, 27))
                .status(CampaignStatus.SCHEDULED)
                .totalSentCount(5)
                .build();

        when(authServiceClient.getTokensBySegment(any()))
                .thenReturn(List.of(new AuthServiceClient.TokenEntry("user-1", "token-1")));
        when(campaignRepository.save(any(NotificationCampaign.class))).thenAnswer(i -> i.getArgument(0));

        campaignService.executeCampaign(campaign, LocalDate.of(2026, 7, 27));

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
        assertThat(campaign.getLastSentDate()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(campaign.getTotalSentCount()).isEqualTo(6);
    }

    @Test
    void executeCampaign_notLastDay_staysScheduled() {
        NotificationCampaign campaign = NotificationCampaign.builder()
                .id("campaign-2")
                .title("t").body("b")
                .campaignType(CampaignType.PROMOTION)
                .sendMode(CampaignSendMode.SCHEDULED)
                .startDate(LocalDate.of(2026, 7, 20))
                .endDate(LocalDate.of(2026, 7, 27))
                .status(CampaignStatus.SCHEDULED)
                .build();

        when(authServiceClient.getTokensBySegment(any()))
                .thenReturn(List.of(new AuthServiceClient.TokenEntry("user-1", "token-1")));
        when(campaignRepository.save(any(NotificationCampaign.class))).thenAnswer(i -> i.getArgument(0));

        campaignService.executeCampaign(campaign, LocalDate.of(2026, 7, 21));

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
        assertThat(campaign.getLastSentDate()).isEqualTo(LocalDate.of(2026, 7, 21));
    }

    @Test
    void executeCampaign_authServiceFails_doesNotUpdateLastSentDate() {
        NotificationCampaign campaign = NotificationCampaign.builder()
                .id("campaign-3")
                .title("t").body("b")
                .campaignType(CampaignType.PROMOTION)
                .sendMode(CampaignSendMode.SCHEDULED)
                .startDate(LocalDate.of(2026, 7, 20))
                .endDate(LocalDate.of(2026, 7, 27))
                .status(CampaignStatus.SCHEDULED)
                .build();

        when(authServiceClient.getTokensBySegment(any())).thenThrow(new RuntimeException("boom"));

        campaignService.executeCampaign(campaign, LocalDate.of(2026, 7, 21));

        assertThat(campaign.getLastSentDate()).isNull();
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
        org.mockito.Mockito.verify(campaignRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void cancelCampaign_rejectsWhenNotScheduled() {
        NotificationCampaign campaign = NotificationCampaign.builder()
                .id("campaign-4")
                .status(CampaignStatus.COMPLETED)
                .build();
        when(campaignRepository.findByIdAndIsDeletedFalse("campaign-4"))
                .thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> campaignService.cancelCampaign("campaign-4"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void cancelCampaign_setsCancelledWhenScheduled() {
        NotificationCampaign campaign = NotificationCampaign.builder()
                .id("campaign-5")
                .status(CampaignStatus.SCHEDULED)
                .build();
        when(campaignRepository.findByIdAndIsDeletedFalse("campaign-5"))
                .thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(NotificationCampaign.class))).thenAnswer(i -> i.getArgument(0));

        var response = campaignService.cancelCampaign("campaign-5");

        assertThat(response.getStatus()).isEqualTo(CampaignStatus.CANCELLED);
    }
}
