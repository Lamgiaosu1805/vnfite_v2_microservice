package com.p2plending.notification.service;

import com.p2plending.notification.client.PushNotificationClient;
import com.p2plending.notification.domain.entity.Notification;
import com.p2plending.notification.domain.enums.NotificationType;
import com.p2plending.notification.domain.repository.NotificationRepository;
import com.p2plending.notification.dto.NotificationResponse;
import com.p2plending.notification.dto.PagedResponse;
import com.p2plending.notification.kafka.event.LoanApprovedAwaitingBorrowerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository  notificationRepository;
    private final PushNotificationClient  pushClient;

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getUserNotifications(String userId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = notificationRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageable);
        return PagedResponse.<NotificationResponse>builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    @Transactional
    public NotificationResponse markAsRead(String notificationId, String userId) {
        Notification notification = notificationRepository
                .findByIdAndUserIdAndIsDeletedFalse(notificationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông báo"));
        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public void notifyLoanApprovedAwaitingBorrower(LoanApprovedAwaitingBorrowerEvent event) {
        String title = "Khoản gọi vốn đã được phê duyệt";
        String message = "Khoản gọi vốn %s đã được phê duyệt với số tiền %s, lãi suất %s/năm, kỳ hạn %s tháng. "
                + "Vui lòng xác nhận điều kiện để đưa khoản gọi vốn lên sàn cho nhà đầu tư.";

        Notification notification = Notification.builder()
                .userId(event.getBorrowerId())
                .title(title)
                .message(message.formatted(
                        event.getLoanCode() != null ? event.getLoanCode() : event.getLoanId(),
                        formatMoney(event.getApprovedAmount()),
                        formatRate(event.getApprovedInterestRate()),
                        event.getTermMonths() != null ? event.getTermMonths() : 0
                ))
                .type(NotificationType.IN_APP)
                .channel("LOAN_APPROVAL")
                .referenceId(event.getLoanId())
                .referenceType("LOAN")
                .sentAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
        log.info("Stored loan approval notification for borrower={} loan={}",
                event.getBorrowerId(), event.getLoanId());

        // Gửi push notification đến thiết bị của borrower
        pushClient.pushToUser(
                event.getBorrowerId(),
                title,
                "Khoản gọi vốn %s đã được phê duyệt. Nhấn để xem và xác nhận điều kiện.".formatted(
                        event.getLoanCode() != null ? event.getLoanCode() : event.getLoanId()),
                Map.of("action", "OPEN_LOAN_DETAIL", "loanId", event.getLoanId())
        );
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) return "0 VND";
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN")).format(value);
    }

    private String formatRate(BigDecimal value) {
        if (value == null) return "0%";
        return value.stripTrailingZeros().toPlainString() + "%";
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .channel(notification.getChannel())
                .referenceId(notification.getReferenceId())
                .referenceType(notification.getReferenceType())
                .read(notification.isRead())
                .sentAt(notification.getSentAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
