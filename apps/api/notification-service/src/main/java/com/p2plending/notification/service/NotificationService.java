package com.p2plending.notification.service;

import com.p2plending.notification.client.PushNotificationClient;
import com.p2plending.notification.domain.entity.Notification;
import com.p2plending.notification.domain.enums.NotificationType;
import com.p2plending.notification.domain.repository.NotificationRepository;
import com.p2plending.notification.dto.NotificationResponse;
import com.p2plending.notification.dto.PagedResponse;
import com.p2plending.notification.kafka.event.ContractReadyEvent;
import com.p2plending.notification.kafka.event.DepositCompletedEvent;
import com.p2plending.notification.kafka.event.InvestmentRefundedEvent;
import com.p2plending.notification.kafka.event.InvestmentCreditReconciledEvent;
import com.p2plending.notification.kafka.event.LoanApprovedAwaitingBorrowerEvent;
import com.p2plending.notification.kafka.event.LoanDisbursedEvent;
import com.p2plending.notification.kafka.event.LoanRepaidEvent;
import com.p2plending.notification.kafka.event.RepaymentDueReminderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
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

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalseAndIsDeletedFalse(userId);
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

    /** Hợp đồng gọi vốn đã sẵn sàng để người gọi vốn ký (khoản vừa đủ vốn). */
    @Transactional
    public void notifyContractReady(ContractReadyEvent event) {
        String code = event.getLoanCode() != null ? event.getLoanCode() : event.getLoanId();
        String title = "Hợp đồng đã sẵn sàng để ký";
        String message = ("Khoản gọi vốn %s đã được đầu tư đủ. Hợp đồng gọi vốn (số tiền %s, lãi suất %s/năm, "
                + "kỳ hạn %s tháng) đã sẵn sàng. Vui lòng ký hợp đồng để hoàn tất.")
                .formatted(code, formatMoney(event.getAmount()), formatRate(event.getInterestRate()),
                        event.getTermMonths() != null ? event.getTermMonths() : 0);

        Notification notification = Notification.builder()
                .userId(event.getBorrowerId())
                .title(title)
                .message(message)
                .type(NotificationType.IN_APP)
                .channel("CONTRACT")
                .referenceId(event.getContractId())
                .referenceType("CONTRACT")
                .sentAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notification);
        log.info("Stored contract.ready notification for borrower={} loan={} contract={}",
                event.getBorrowerId(), event.getLoanId(), event.getContractId());

        pushClient.pushToUser(
                event.getBorrowerId(),
                title,
                "Khoản gọi vốn %s đã sẵn sàng để ký hợp đồng. Nhấn để ký.".formatted(code),
                Map.of("action", "OPEN_CONTRACT_SIGN",
                        "loanId", event.getLoanId(),
                        "contractId", event.getContractId())
        );
    }

    /** Vốn đã được giải ngân — thông báo cho người gọi vốn và các nhà đầu tư. */
    @Transactional
    public void notifyLoanDisbursed(LoanDisbursedEvent event) {
        String code = event.getLoanCode() != null ? event.getLoanCode() : event.getLoanId();

        // Người gọi vốn — đã nhận vốn
        String bTitle = "Đã nhận vốn";
        String bMessage = "Khoản gọi vốn %s đã được giải ngân với số tiền %s. Bạn đã nhận vốn thành công."
                .formatted(code, formatMoney(event.getAmount()));
        notificationRepository.save(Notification.builder()
                .userId(event.getBorrowerId())
                .title(bTitle)
                .message(bMessage)
                .type(NotificationType.IN_APP)
                .channel("DISBURSEMENT")
                .referenceId(event.getLoanId())
                .referenceType("LOAN")
                .sentAt(LocalDateTime.now())
                .build());
        pushClient.pushToUser(event.getBorrowerId(), bTitle,
                "Khoản gọi vốn %s đã được giải ngân. Nhấn để xem lịch thanh toán.".formatted(code),
                Map.of("action", "OPEN_LOAN_DETAIL", "loanId", event.getLoanId()));

        // Nhà đầu tư — khoản đã giải ngân
        List<String> investorIds = event.getInvestorIds();
        if (investorIds != null) {
            for (String investorId : investorIds) {
                String iTitle = "Khoản đầu tư đã giải ngân";
                String iMessage = "Khoản %s bạn đầu tư đã được giải ngân. Bạn sẽ bắt đầu nhận dòng tiền theo lịch."
                        .formatted(code);
                notificationRepository.save(Notification.builder()
                        .userId(investorId)
                        .title(iTitle)
                        .message(iMessage)
                        .type(NotificationType.IN_APP)
                        .channel("DISBURSEMENT")
                        .referenceId(event.getLoanId())
                        .referenceType("LOAN")
                        .sentAt(LocalDateTime.now())
                        .build());
                pushClient.pushToUser(investorId, iTitle,
                        "Khoản %s bạn đầu tư đã được giải ngân.".formatted(code),
                        Map.of("action", "OPEN_CASHFLOW", "loanId", event.getLoanId()));
            }
        }
        log.info("Stored loan.disbursed notifications for loan={} borrower={} investors={}",
                event.getLoanId(), event.getBorrowerId(),
                investorIds != null ? investorIds.size() : 0);
    }

    @Transactional
    public void notifyInvestmentRefunded(InvestmentRefundedEvent event) {
        String code = event.getLoanCode() != null ? event.getLoanCode() : event.getLoanId();
        List<InvestmentRefundedEvent.Refund> refunds = event.getRefunds();
        if (refunds == null || refunds.isEmpty()) return;

        for (InvestmentRefundedEvent.Refund r : refunds) {
            String title = "Đã hoàn tiền đầu tư";
            String message = "Khoản %s đã đóng. Số tiền %s bạn đặt đầu tư đã được hoàn về số dư khả dụng."
                    .formatted(code, formatMoney(r.getAmount()));
            notificationRepository.save(Notification.builder()
                    .userId(r.getInvestorId())
                    .title(title)
                    .message(message)
                    .type(NotificationType.IN_APP)
                    .channel("REFUND")
                    .referenceId(event.getLoanId())
                    .referenceType("LOAN")
                    .sentAt(LocalDateTime.now())
                    .build());
            pushClient.pushToUser(r.getInvestorId(), title,
                    "Khoản %s: đã hoàn %s về ví của bạn.".formatted(code, formatMoney(r.getAmount())),
                    Map.of("action", "OPEN_WALLET", "loanId", event.getLoanId()));
        }
        log.info("Stored investment.refunded notifications for loan={} count={}",
                event.getLoanId(), refunds.size());
    }

    /** Người gọi vốn trả một kỳ — thông báo cho người gọi vốn và từng nhà đầu tư nhận tiền. */
    @Transactional
    public void notifyLoanRepaid(LoanRepaidEvent event) {
        String code = event.getLoanCode() != null ? event.getLoanCode() : event.getLoanId();
        String periodLabel = event.getTotalPeriods() != null
                ? "%d/%d".formatted(event.getPeriodNumber(), event.getTotalPeriods())
                : String.valueOf(event.getPeriodNumber());

        // ── Người gọi vốn ──
        String bTitle;
        String bMessage;
        if (event.isLoanCompleted()) {
            bTitle = "Đã tất toán khoản vay";
            bMessage = "Bạn đã trả hết khoản gọi vốn %s. Cảm ơn bạn đã hoàn tất nghĩa vụ thanh toán."
                    .formatted(code);
        } else if (event.isPartial()) {
            BigDecimal remaining = event.getPeriodTotalDue() != null && event.getAmountPaid() != null
                    ? event.getPeriodTotalDue().subtract(event.getAmountPaid()) : null;
            bTitle = "Đã trả một phần kỳ " + periodLabel;
            bMessage = ("Bạn vừa trả %s cho kỳ %s khoản %s. " + (remaining != null
                        ? "Còn thiếu %s — vui lòng nạp thêm để hoàn tất kỳ này."
                        : "Vui lòng nạp thêm để hoàn tất kỳ này."))
                    .formatted(formatMoney(event.getAmountPaid()), periodLabel, code,
                            remaining != null ? formatMoney(remaining) : "");
        } else {
            bTitle = "Đã trả nợ kỳ " + periodLabel;
            bMessage = "Bạn đã trả %s cho kỳ %s khoản gọi vốn %s thành công."
                    .formatted(formatMoney(event.getAmountPaid()), periodLabel, code);
        }

        notificationRepository.save(Notification.builder()
                .userId(event.getBorrowerId())
                .title(bTitle)
                .message(bMessage)
                .type(NotificationType.IN_APP)
                .channel("REPAYMENT")
                .referenceId(event.getLoanId())
                .referenceType("LOAN")
                .sentAt(LocalDateTime.now())
                .build());
        pushClient.pushToUser(event.getBorrowerId(), bTitle, bMessage,
                Map.of("action", "OPEN_LOAN_DETAIL", "loanId", event.getLoanId()));

        // ── Nhà đầu tư nhận tiền ──
        List<LoanRepaidEvent.InvestorPayout> payouts = event.getInvestorPayouts();
        if (payouts != null) {
            for (LoanRepaidEvent.InvestorPayout p : payouts) {
                String iTitle = "Nhận tiền hoàn trả";
                String iMessage = "Khoản %s vừa trả kỳ %s. Bạn nhận %s vào ví VNFITE."
                        .formatted(code, periodLabel, formatMoney(p.getAmount()));
                notificationRepository.save(Notification.builder()
                        .userId(p.getInvestorId())
                        .title(iTitle)
                        .message(iMessage)
                        .type(NotificationType.IN_APP)
                        .channel("REPAYMENT")
                        .referenceId(event.getLoanId())
                        .referenceType("LOAN")
                        .sentAt(LocalDateTime.now())
                        .build());
                pushClient.pushToUser(p.getInvestorId(), iTitle, iMessage,
                        Map.of("action", "OPEN_WALLET", "loanId", event.getLoanId()));
            }
        }

        log.info("Stored loan.repayment_completed notifications for loan={} borrower={} investors={} completed={}",
                event.getLoanId(), event.getBorrowerId(),
                payouts != null ? payouts.size() : 0, event.isLoanCompleted());
    }

    /** Đến hạn nhưng ví không đủ — nhắc người gọi vốn nạp tiền (đẩy hằng ngày tới khi trả/đủ). */
    @Transactional
    public void notifyRepaymentDueReminder(RepaymentDueReminderEvent event) {
        String code = event.getLoanCode() != null ? event.getLoanCode() : event.getLoanId();
        String periodLabel = event.getTotalPeriods() != null
                ? "%d/%d".formatted(event.getPeriodNumber(), event.getTotalPeriods())
                : String.valueOf(event.getPeriodNumber());
        boolean overdue = event.getDpd() > 0;

        String title = overdue ? "Khoản vay đã quá hạn" : "Đến hạn trả nợ";
        String message = overdue
                ? "Kỳ %s khoản %s đã quá hạn %d ngày. Số tiền cần trả %s nhưng ví chưa đủ — vui lòng nạp tiền để tránh phí phạt tăng thêm."
                        .formatted(periodLabel, code, event.getDpd(), formatMoney(event.getAmountDue()))
                : "Kỳ %s khoản %s đến hạn hôm nay (%s). Ví của bạn chưa đủ số dư — vui lòng nạp tiền để hệ thống tự động thu."
                        .formatted(periodLabel, code, formatMoney(event.getAmountDue()));

        notificationRepository.save(Notification.builder()
                .userId(event.getBorrowerId())
                .title(title)
                .message(message)
                .type(NotificationType.IN_APP)
                .channel("REPAYMENT_DUE")
                .referenceId(event.getLoanId())
                .referenceType("LOAN")
                .sentAt(LocalDateTime.now())
                .build());
        pushClient.pushToUser(event.getBorrowerId(), title, message,
                Map.of("action", "OPEN_WALLET", "loanId", event.getLoanId()));

        log.info("Stored repayment due reminder for borrower={} loan={} period={} dpd={}",
                event.getBorrowerId(), event.getLoanId(), event.getPeriodNumber(), event.getDpd());
    }

    /** Đối soát đã cộng bù tiền hoàn trả cho nhà đầu tư (lần trả trước bị lỗi cộng ví). */
    @Transactional
    public void notifyCreditReconciled(InvestmentCreditReconciledEvent event) {
        String code = event.getLoanCode() != null ? event.getLoanCode() : event.getLoanId();
        String title = "Nhận tiền hoàn trả";
        String message = "Khoản %s đã hoàn trả %s vào ví VNFITE của bạn."
                .formatted(code, formatMoney(event.getAmount()));

        notificationRepository.save(Notification.builder()
                .userId(event.getInvestorId())
                .title(title)
                .message(message)
                .type(NotificationType.IN_APP)
                .channel("REPAYMENT")
                .referenceId(event.getLoanId())
                .referenceType("LOAN")
                .sentAt(LocalDateTime.now())
                .build());
        pushClient.pushToUser(event.getInvestorId(), title, message,
                Map.of("action", "OPEN_WALLET", "loanId", event.getLoanId()));

        log.info("Stored reconciled credit notification for investor={} loan={} amount={}",
                event.getInvestorId(), event.getLoanId(), event.getAmount());
    }

    @Transactional
    public void notifyDepositCompleted(DepositCompletedEvent event) {
        String title = "Nạp tiền thành công";
        String message = "Ví VNFITE của bạn vừa nhận %s. Số dư hiện tại: %s."
                .formatted(formatMoney(event.getAmount()), formatMoney(event.getBalance()));

        notificationRepository.save(Notification.builder()
                .userId(event.getUserId())
                .title(title)
                .message(message)
                .type(NotificationType.IN_APP)
                .channel("WALLET")
                .referenceId(event.getTxnId())
                .referenceType("DEPOSIT")
                .sentAt(LocalDateTime.now())
                .build());

        log.info("Stored deposit notification for userId={} amount={} balance={}",
                event.getUserId(), event.getAmount(), event.getBalance());

        pushClient.pushToUser(
                event.getUserId(),
                title,
                "Ví VNFITE của bạn vừa nhận %s.".formatted(formatMoney(event.getAmount())),
                Map.of("action", "OPEN_WALLET", "txnId", event.getTxnId() != null ? event.getTxnId() : "")
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
