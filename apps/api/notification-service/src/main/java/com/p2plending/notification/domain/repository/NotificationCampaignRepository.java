package com.p2plending.notification.domain.repository;

import com.p2plending.notification.domain.entity.NotificationCampaign;
import com.p2plending.notification.domain.enums.CampaignSendMode;
import com.p2plending.notification.domain.enums.CampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface NotificationCampaignRepository extends JpaRepository<NotificationCampaign, String> {

    Page<NotificationCampaign> findByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    Optional<NotificationCampaign> findByIdAndIsDeletedFalse(String id);

    /**
     * Campaign đến hạn bắn hôm nay: còn SCHEDULED, đang trong khoảng ngày, chưa gửi hôm nay,
     * và giờ hiện tại đã tới/qua giờ đặt lịch (>= để tự "đuổi kịp" nếu service down đúng phút đó).
     */
    @Query("SELECT c FROM NotificationCampaign c WHERE c.isDeleted = false " +
           "AND c.status = :status AND c.sendMode = :sendMode " +
           "AND c.startDate <= :today AND c.endDate >= :today " +
           "AND (c.lastSentDate IS NULL OR c.lastSentDate < :today) " +
           "AND c.scheduledTime <= :now")
    List<NotificationCampaign> findDueCampaigns(
            @Param("status") CampaignStatus status,
            @Param("sendMode") CampaignSendMode sendMode,
            @Param("today") LocalDate today,
            @Param("now") LocalTime now);
}
