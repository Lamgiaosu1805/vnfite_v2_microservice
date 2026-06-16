package com.p2plending.loan.service;

import com.p2plending.loan.client.PaymentServiceClient;
import com.p2plending.loan.config.CacheConfig;
import com.p2plending.loan.domain.entity.LoanOffer;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.entity.PendingInvestorCredit;
import com.p2plending.loan.domain.entity.RepaymentSchedule;
import com.p2plending.loan.domain.entity.RepaymentTransaction;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.OfferStatus;
import com.p2plending.loan.domain.enums.PaymentChannel;
import com.p2plending.loan.domain.enums.PendingCreditStatus;
import com.p2plending.loan.domain.enums.RepaymentMethod;
import com.p2plending.loan.domain.enums.RepaymentStatus;
import com.p2plending.loan.domain.repository.LoanOfferRepository;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.domain.repository.PendingInvestorCreditRepository;
import com.p2plending.loan.domain.repository.RepaymentScheduleRepository;
import com.p2plending.loan.domain.repository.RepaymentTransactionRepository;
import com.p2plending.loan.dto.request.RecordPaymentRequest;
import com.p2plending.loan.dto.response.RepaymentScheduleResponse;
import com.p2plending.loan.exception.InvalidLoanStateException;
import com.p2plending.loan.exception.LoanNotFoundException;
import com.p2plending.loan.kafka.KafkaProducerService;
import com.p2plending.loan.kafka.event.InvestmentCreditReconciledEvent;
import com.p2plending.loan.kafka.event.LoanRepaidEvent;
import com.p2plending.loan.kafka.event.RepaymentDueReminderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepaymentService {

    private static final ZoneId TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Trạng thái khoản chấp nhận ghi nhận trả nợ (đã giải ngân + có lịch). */
    private static final Set<LoanStatus> PAYABLE_STATUSES =
            EnumSet.of(LoanStatus.DISBURSED, LoanStatus.REPAYING, LoanStatus.DEFAULTED);

    /** Khoản đang trong vòng đời trả nợ — đối tượng quét DPD. */
    private static final Set<LoanStatus> SERVICING_STATUSES =
            EnumSet.of(LoanStatus.DISBURSED, LoanStatus.REPAYING);

    private final RepaymentScheduleRepository      scheduleRepository;
    private final RepaymentTransactionRepository   transactionRepository;
    private final LoanRequestRepository            loanRequestRepository;
    private final LoanOfferRepository              loanOfferRepository;
    private final PendingInvestorCreditRepository  pendingCreditRepository;
    private final LoanProductService               loanProductService;
    private final RepaymentScheduleGenerator       generator;
    private final PaymentServiceClient             paymentServiceClient;
    private final KafkaProducerService             kafkaProducerService;
    private final CacheManager                     cacheManager;

    /** Ngưỡng DPD đánh DEFAULTED — VNFITE dùng 30 ngày (siết chặt hơn chuẩn NHNN 90 ngày). */
    @Value("${app.dpd.default-threshold:30}")
    private int defaultDpdThreshold;

    /** Bật/tắt tính phí phạt trả chậm (kill-switch cho tính năng tiền). */
    @Value("${app.late-fee.enabled:true}")
    private boolean lateFeeEnabled;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final BigDecimal DEFAULT_LATE_FEE_RATE = new BigDecimal("150.00");

    // ── Sinh lịch khi FUNDED ──────────────────────────────────────

    /** Sinh lịch trả nợ cho khoản vừa FUNDED. Idempotent — bỏ qua nếu đã có lịch. */
    @Transactional
    public void generateSchedule(LoanRequest loan) {
        if (scheduleRepository.existsByLoanIdAndIsDeletedFalse(loan.getId())) {
            log.warn("Repayment schedule already exists for loan {} — skip generation", loan.getId());
            return;
        }
        if (loan.getInterestRate() == null) {
            log.error("Cannot generate schedule for loan {} — interestRate is null", loan.getId());
            return;
        }

        RepaymentMethod method = resolveMethod(loan);
        LocalDate fundedDate = LocalDate.now(TZ);

        List<RepaymentSchedule> schedule = generator.generate(
                loan.getAmount(), loan.getInterestRate(), loan.getTermMonths(), method, fundedDate,
                loan.getRepaymentDay());
        schedule.forEach(s -> s.setLoanId(loan.getId()));
        scheduleRepository.saveAll(schedule);

        int totalPeriods = schedule.size();
        String firstDueInfo = schedule.isEmpty() ? "N/A" : schedule.get(0).getDueDate().toString();
        log.info("Generated {}-period repayment schedule for loan {} (method={}, repaymentDay={}, firstDue={})",
                totalPeriods, loan.getId(), method, loan.getRepaymentDay(), firstDueInfo);
    }

    private RepaymentMethod resolveMethod(LoanRequest loan) {
        if (loan.getProductId() == null) return RepaymentMethod.EMI_MONTHLY;
        return loanProductService.findProductById(loan.getProductId())
                .map(p -> p.getRepaymentMethod() != null ? p.getRepaymentMethod() : RepaymentMethod.EMI_MONTHLY)
                .orElse(RepaymentMethod.EMI_MONTHLY);
    }

    // ── Ghi nhận thanh toán ───────────────────────────────────────

    /** Áp tiền trả vào các kỳ theo thứ tự (kỳ sớm trước), ghi giao dịch, cập nhật trạng thái khoản. */
    @Transactional
    public List<RepaymentScheduleResponse> recordPayment(String loanId, RecordPaymentRequest request) {
        LoanRequest loan = loanRequestRepository.findById(loanId)
                .orElseThrow(() -> new LoanNotFoundException(loanId));

        if (!PAYABLE_STATUSES.contains(loan.getStatus())) {
            throw new InvalidLoanStateException(
                    "Khoản gọi vốn %s không ở trạng thái nhận trả nợ (status: %s)"
                    .formatted(loan.getLoanCode(), loan.getStatus()));
        }

        List<RepaymentSchedule> schedules =
                scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(loanId);
        if (schedules.isEmpty()) {
            throw new InvalidLoanStateException(
                    "Khoản gọi vốn %s chưa có lịch trả nợ".formatted(loan.getLoanCode()));
        }

        // Guard: nếu kỳ đầu chưa trả đã có giao dịch ví → không cho ghi đè bằng manual path
        List<PaymentChannel> walletChannels = List.of(PaymentChannel.WALLET, PaymentChannel.AUTO_DEBIT);
        schedules.stream()
                .filter(s -> !s.isSettled() && s.getRemainingDue().signum() > 0)
                .findFirst()
                .ifPresent(firstUnpaid -> {
                    if (transactionRepository.existsByScheduleIdAndChannelInAndIsDeletedFalse(
                            firstUnpaid.getId(), walletChannels)) {
                        throw new InvalidLoanStateException(
                                "Kỳ %d của khoản %s đã có giao dịch qua ví VNFITE. "
                                        .formatted(firstUnpaid.getPeriodNumber(), loan.getLoanCode()) +
                                "Không được ghi đè bằng manual path — dùng luồng ví hoặc liên hệ kỹ thuật.");
                    }
                });

        LocalDateTime paidAt = request.getPaidAt() != null ? request.getPaidAt() : LocalDateTime.now(TZ);
        BigDecimal remaining = request.getAmount();
        String firstTouchedScheduleId = null;

        for (RepaymentSchedule s : schedules) {
            if (remaining.signum() <= 0) break;
            if (s.isSettled()) continue;

            BigDecimal due = s.getRemainingDue();
            if (due.signum() <= 0) continue;

            BigDecimal applied = remaining.min(due);
            s.setPaidAmount(s.getPaidAmount().add(applied));
            remaining = remaining.subtract(applied);
            if (firstTouchedScheduleId == null) firstTouchedScheduleId = s.getId();

            if (s.getRemainingDue().signum() <= 0) {
                s.setStatus(RepaymentStatus.PAID);
                s.setPaidAt(paidAt);
                s.setDpd(0);
            } else {
                s.setStatus(RepaymentStatus.PARTIAL);
            }
        }
        scheduleRepository.saveAll(schedules);

        String noteWithReason = "[REASON] " + request.getReason()
                + (request.getNote() != null ? " | " + request.getNote() : "");
        transactionRepository.save(RepaymentTransaction.builder()
                .loanId(loanId)
                .scheduleId(firstTouchedScheduleId)
                .amount(request.getAmount())
                .paidAt(paidAt)
                .channel(request.getChannel() != null ? request.getChannel() : PaymentChannel.MANUAL_ADMIN)
                .externalRef(request.getExternalRef())
                .recordedBy(request.getRecordedBy())
                .note(noteWithReason)
                .build());

        applyLoanStatusAfterPayment(loan, schedules);
        evictLoanCaches(loanId);

        if (remaining.signum() > 0) {
            log.warn("Loan {} payment had {} VND unapplied (overpayment)", loanId, remaining);
        }
        log.info("Recorded payment {} VND for loan {} — new status {}",
                request.getAmount(), loanId, loan.getStatus());

        return schedules.stream().map(this::toResponse).toList();
    }

    private void applyLoanStatusAfterPayment(LoanRequest loan, List<RepaymentSchedule> schedules) {
        boolean allPaid = schedules.stream().allMatch(RepaymentSchedule::isSettled);
        LoanStatus next = allPaid ? LoanStatus.COMPLETED : LoanStatus.REPAYING;
        if (loan.getStatus() != next) {
            loan.setStatus(next);
            loanRequestRepository.save(loan);
        }
    }

    // ── Trả nợ từ ví: chuyển nội bộ người gọi vốn → nhà đầu tư ─────

    /**
     * App: người gọi vốn chủ động trả kỳ chưa thanh toán sớm nhất từ ví VNFITE (trả đủ kỳ).
     * Fail-closed: ví không đủ số dư → ném InvalidLoanStateException (→ 409), không trả nợ.
     */
    @Transactional
    public List<RepaymentScheduleResponse> repayNextDueFromWallet(String loanId, String borrowerId) {
        LoanRequest loan = loanRequestRepository.findById(loanId)
                .orElseThrow(() -> new LoanNotFoundException(loanId));

        if (!loan.getBorrowerId().equals(borrowerId)) {
            throw new InvalidLoanStateException("Bạn không có quyền trả nợ khoản gọi vốn này");
        }
        if (!PAYABLE_STATUSES.contains(loan.getStatus())) {
            throw new InvalidLoanStateException(
                    "Khoản gọi vốn %s không ở trạng thái nhận trả nợ (status: %s)"
                    .formatted(loan.getLoanCode(), loan.getStatus()));
        }

        List<RepaymentSchedule> schedules =
                scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(loanId);
        RepaymentSchedule period = firstUnpaid(schedules)
                .orElseThrow(() -> new InvalidLoanStateException(
                        "Khoản gọi vốn %s không còn kỳ nào cần trả".formatted(loan.getLoanCode())));

        // Trả đủ kỳ: gốc + lãi + phí phạt còn lại.
        List<RepaymentSchedule> updated = doSettlePeriod(
                loan, period, period.getTotalOutstanding(), PaymentChannel.WALLET, borrowerId, schedules.size());
        return updated.stream().map(this::toResponse).toList();
    }

    /** ID các khoản đang trả nợ — scheduler đọc riêng để auto-debit từng khoản trong transaction độc lập. */
    @Transactional(readOnly = true)
    public List<String> findAutoDebitLoanIds() {
        return loanRequestRepository.findByStatusInAndIsDeletedFalse(SERVICING_STATUSES)
                .stream().map(LoanRequest::getId).toList();
    }

    /**
     * Scheduler: tự động trừ ví người gọi vốn cho kỳ đến hạn sớm nhất (dueDate ≤ hôm nay).
     * Số dư đủ → trả đủ kỳ; số dư thiếu → trả một phần; không có số dư → bỏ qua (DPD đánh OVERDUE sau).
     * Mỗi khoản một transaction riêng để một khoản lỗi không ảnh hưởng khoản khác.
     */
    @Transactional
    public void autoDebitLoan(String loanId) {
        LoanRequest loan = loanRequestRepository.findById(loanId).orElse(null);
        if (loan == null || !SERVICING_STATUSES.contains(loan.getStatus())) return;

        List<RepaymentSchedule> schedules =
                scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(loanId);
        LocalDate today = LocalDate.now(TZ);
        RepaymentSchedule due = firstUnpaid(schedules)
                .filter(s -> !s.getDueDate().isAfter(today))   // chỉ trừ khi đã tới hạn
                .orElse(null);
        if (due == null) return;

        BigDecimal available;
        try {
            available = paymentServiceClient.getAvailableBalance(loan.getBorrowerId());
        } catch (Exception e) {
            log.warn("Auto-debit loan {}: không lấy được số dư ví borrower {}: {}",
                    loanId, loan.getBorrowerId(), e.getMessage());
            return;
        }
        if (available == null || available.signum() <= 0) {
            log.info("Auto-debit loan {}: ví borrower không đủ số dư (available={}) — nhắc nạp tiền, để DPD đánh quá hạn",
                    loanId, available);
            kafkaProducerService.publishRepaymentDueReminder(RepaymentDueReminderEvent.builder()
                    .loanId(loan.getId())
                    .loanCode(loan.getLoanCode())
                    .borrowerId(loan.getBorrowerId())
                    .periodNumber(due.getPeriodNumber())
                    .totalPeriods(schedules.size())
                    .amountDue(due.getTotalOutstanding())
                    .dueDate(due.getDueDate())
                    .dpd(due.getDpd() != null ? due.getDpd() : 0)
                    .build());
            return;
        }

        BigDecimal payAmount = available.min(due.getTotalOutstanding());
        doSettlePeriod(loan, due, payAmount, PaymentChannel.AUTO_DEBIT, null, schedules.size());
    }

    /**
     * Lõi trả nợ một kỳ: trừ ví người gọi vốn (fail-closed) → áp vào lịch → ghi giao dịch →
     * phân bổ pro-rata về ví nhà đầu tư → cập nhật trạng thái khoản → phát event thông báo.
     * Chạy trong transaction của caller. Vế trừ borrower fail-closed (ném → rollback). Vế cộng
     * investor best-effort (log để đối soát, không rollback → tránh trừ trùng borrower lần sau).
     *
     * @param payAmount số tiền trả lần này (caller đảm bảo 0 &lt; payAmount ≤ remainingDue của kỳ)
     */
    private List<RepaymentSchedule> doSettlePeriod(LoanRequest loan, RepaymentSchedule period,
                                                   BigDecimal payAmount, PaymentChannel channel,
                                                   String recordedBy, int totalPeriods) {
        LocalDateTime paidAt = LocalDateTime.now(TZ);
        // Khóa idempotent ổn định theo (khoản, kỳ, TỔNG đã trả TRƯỚC giao dịch này = gốc+lãi+phí phạt).
        // Mỗi lần trả thành công làm tổng đã trả tăng → lần trả kế tiếp (kể cả cùng ngày: auto-debit một
        // phần rồi trả tay) có khóa khác, không bị skip nhầm. Còn retry của CÙNG một lần trả đã rollback
        // thấy tổng chưa đổi → cùng khóa → debit idempotent-skip an toàn (không trừ trùng người gọi vốn).
        BigDecimal lateFeePaidBefore = period.getLateFeePaid() != null ? period.getLateFeePaid() : BigDecimal.ZERO;
        String settledBefore = period.getPaidAmount().add(lateFeePaidBefore).toPlainString();
        String baseRef = "%s-P%d-%s".formatted(loan.getId(), period.getPeriodNumber(), settledBefore);
        String borrowerRef = "REPAY-OUT-" + baseRef;

        // 1) Trừ ví người gọi vốn (fail-closed: ví thiếu → ném → rollback toàn bộ)
        paymentServiceClient.debitRepayment(loan.getBorrowerId(), payAmount,
                "Trả nợ kỳ %d khoản %s".formatted(period.getPeriodNumber(), loan.getLoanCode()),
                borrowerRef);

        // 2) Áp tiền vào kỳ — ưu tiên gốc+lãi trước, phần dư trả vào phí phạt
        BigDecimal remaining = payAmount;
        BigDecimal toPrincipalInterest = remaining.min(period.getRemainingDue());
        period.setPaidAmount(period.getPaidAmount().add(toPrincipalInterest));
        remaining = remaining.subtract(toPrincipalInterest);

        BigDecimal toLateFee = remaining.min(period.getLateFeeOutstanding());
        if (toLateFee.signum() > 0) {
            period.setLateFeePaid(lateFeePaidBefore.add(toLateFee));
        }

        boolean periodSettled = period.getTotalOutstanding().signum() <= 0;
        if (periodSettled) {
            period.setStatus(RepaymentStatus.PAID);
            period.setPaidAt(paidAt);
            period.setDpd(0);
        } else {
            period.setStatus(RepaymentStatus.PARTIAL);
        }
        scheduleRepository.save(period);

        // 3) Ghi giao dịch trả nợ
        transactionRepository.save(RepaymentTransaction.builder()
                .loanId(loan.getId())
                .scheduleId(period.getId())
                .amount(payAmount)
                .paidAt(paidAt)
                .channel(channel)
                .externalRef(borrowerRef)
                .recordedBy(recordedBy)
                .note("Trả nợ kỳ %d".formatted(period.getPeriodNumber()))
                .build());

        // 4) Phân bổ pro-rata về ví nhà đầu tư (best-effort, idempotent)
        List<LoanRepaidEvent.InvestorPayout> payouts =
                distributeToInvestors(loan, payAmount, "REPAY-IN-" + baseRef);

        // 5) Cập nhật trạng thái khoản
        List<RepaymentSchedule> all =
                scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(loan.getId());
        boolean allPaid = all.stream().allMatch(RepaymentSchedule::isSettled);
        LoanStatus next = allPaid ? LoanStatus.COMPLETED : LoanStatus.REPAYING;
        if (loan.getStatus() != next) {
            loan.setStatus(next);
            loanRequestRepository.save(loan);
        }
        evictLoanCaches(loan.getId());

        // 6) Phát event thông báo (sau khi tiền đã trừ/cộng + trạng thái cập nhật)
        kafkaProducerService.publishLoanRepaid(LoanRepaidEvent.builder()
                .loanId(loan.getId())
                .loanCode(loan.getLoanCode())
                .borrowerId(loan.getBorrowerId())
                .periodNumber(period.getPeriodNumber())
                .totalPeriods(totalPeriods)
                .amountPaid(payAmount)
                .periodTotalDue(period.getTotalDue())
                .partial(!periodSettled)
                .loanCompleted(allPaid)
                .channel(channel.name())
                .paidAt(paidAt)
                .investorPayouts(payouts)
                .build());

        log.info("Settled {} VND on loan {} period {} (channel={}, periodSettled={}, loanCompleted={}, payouts={})",
                payAmount, loan.getId(), period.getPeriodNumber(), channel, periodSettled, allPaid, payouts.size());
        return all;
    }

    /**
     * Chia payAmount cho nhà đầu tư theo tỉ lệ số tiền đã đầu tư (offer ACCEPTED), làm tròn 2 chữ số,
     * dồn phần dư vào nhà đầu tư cuối để tổng khớp tuyệt đối. Cộng ví best-effort: lỗi từng người
     * được log để đối soát, không làm hỏng giao dịch (borrower đã bị trừ thành công).
     */
    private List<LoanRepaidEvent.InvestorPayout> distributeToInvestors(
            LoanRequest loan, BigDecimal payAmount, String creditRefBase) {
        List<LoanRepaidEvent.InvestorPayout> payouts = new ArrayList<>();
        List<LoanOffer> offers = loanOfferRepository
                .findByLoanRequestIdAndStatus(loan.getId(), OfferStatus.ACCEPTED);
        if (offers.isEmpty()) {
            log.warn("Loan {} không có offer ACCEPTED — {} VND không phân bổ được", loan.getId(), payAmount);
            return payouts;
        }

        BigDecimal totalFunded = offers.stream()
                .map(LoanOffer::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalFunded.signum() <= 0) {
            log.error("Loan {} totalFunded={} không hợp lệ — bỏ qua phân bổ", loan.getId(), totalFunded);
            return payouts;
        }

        BigDecimal distributed = BigDecimal.ZERO;
        for (int i = 0; i < offers.size(); i++) {
            LoanOffer offer = offers.get(i);
            BigDecimal share = (i == offers.size() - 1)
                    ? payAmount.subtract(distributed)   // dồn phần dư làm tròn vào người cuối
                    : payAmount.multiply(offer.getAmount()).divide(totalFunded, 2, RoundingMode.DOWN);
            distributed = distributed.add(share);
            if (share.signum() <= 0) continue;

            String creditRef = creditRefBase + "-" + offer.getId();
            try {
                paymentServiceClient.creditRepayment(offer.getInvestorId(), share,
                        "Nhận hoàn trả khoản %s".formatted(loan.getLoanCode()), creditRef);
                payouts.add(LoanRepaidEvent.InvestorPayout.builder()
                        .investorId(offer.getInvestorId())
                        .amount(share)
                        .build());
            } catch (Exception e) {
                // Cộng lỗi → ghi vào hàng đợi đối soát để job thử lại idempotent (không rollback,
                // tránh trừ trùng người gọi vốn). Người gọi vốn đã bị trừ thành công ở bước trước.
                log.error("ĐỐI SOÁT: cộng hoàn trả thất bại loan={} investor={} offer={} amount={} ref={}: {}",
                        loan.getId(), offer.getInvestorId(), offer.getId(), share, creditRef, e.getMessage());
                if (!pendingCreditRepository.existsByReferenceId(creditRef)) {
                    pendingCreditRepository.save(PendingInvestorCredit.builder()
                            .loanId(loan.getId())
                            .loanCode(loan.getLoanCode())
                            .investorId(offer.getInvestorId())
                            .offerId(offer.getId())
                            .amount(share)
                            .referenceId(creditRef)
                            .description("Nhận hoàn trả khoản %s".formatted(loan.getLoanCode()))
                            .build());
                }
            }
        }
        return payouts;
    }

    /** Kỳ chưa thanh toán xong sớm nhất (còn nợ gốc+lãi hoặc phí phạt). */
    private java.util.Optional<RepaymentSchedule> firstUnpaid(List<RepaymentSchedule> schedules) {
        return schedules.stream()
                .filter(s -> !s.isSettled() && s.getTotalOutstanding().signum() > 0)
                .findFirst();
    }

    // ── Đọc lịch ──────────────────────────────────────────────────

    // ── Đọc lịch ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RepaymentScheduleResponse> getSchedule(String loanId) {
        if (!loanRequestRepository.existsById(loanId)) {
            throw new LoanNotFoundException(loanId);
        }
        return scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(loanId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Quét DPD (gọi từ scheduler) ───────────────────────────────

    /**
     * Cập nhật DPD cho mọi khoản đang trả nợ, đánh OVERDUE các kỳ quá hạn,
     * và chuyển DEFAULTED khi DPD vượt ngưỡng.
     *
     * @return số khoản bị đánh DEFAULTED trong lượt quét này
     */
    @Transactional
    public int runDpdSweep() {
        LocalDate today = LocalDate.now(TZ);
        List<LoanRequest> loans = loanRequestRepository.findByStatusInAndIsDeletedFalse(SERVICING_STATUSES);
        int defaulted = 0;

        for (LoanRequest loan : loans) {
            List<RepaymentSchedule> schedules =
                    scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(loan.getId());
            int maxDpd = 0;
            BigDecimal lateFeeRate = resolveLateFeeRate(loan);

            for (RepaymentSchedule s : schedules) {
                if (s.isSettled()) continue;
                if (s.getDueDate().isBefore(today)) {
                    int dpd = (int) ChronoUnit.DAYS.between(s.getDueDate(), today);
                    s.setDpd(dpd);
                    s.setStatus(RepaymentStatus.OVERDUE);
                    // Phí phạt theo dpd. KHÔNG cho giảm: phí đã phát sinh thì giữ nguyên (max với mức cũ),
                    // nếu không khi borrower trả hết gốc+lãi (remainingDue=0) lần quét sau sẽ tính ra 0 và
                    // xóa mất phí phạt còn nợ. Lấy max nên cũng luôn ≥ phần phí đã trả.
                    BigDecimal fee = computeLateFee(s.getRemainingDue(), loan.getInterestRate(), lateFeeRate, dpd);
                    BigDecimal current = s.getLateFee() != null ? s.getLateFee() : BigDecimal.ZERO;
                    s.setLateFee(fee.max(current));
                    maxDpd = Math.max(maxDpd, dpd);
                }
            }
            scheduleRepository.saveAll(schedules);

            if (maxDpd > defaultDpdThreshold && loan.getStatus() != LoanStatus.DEFAULTED) {
                loan.setStatus(LoanStatus.DEFAULTED);
                loanRequestRepository.save(loan);
                evictLoanCaches(loan.getId());
                defaulted++;
                log.warn("Loan {} marked DEFAULTED — maxDPD={} > threshold {}",
                        loan.getId(), maxDpd, defaultDpdThreshold);
            }
        }

        log.info("DPD sweep done: {} servicing loans scanned, {} newly defaulted", loans.size(), defaulted);
        return defaulted;
    }

    // ── Đối soát cộng tiền hoàn trả lỗi (gọi từ scheduler) ────────

    /** ID các khoản cộng hoàn trả còn PENDING — scheduler đọc riêng để retry từng cái độc lập. */
    @Transactional(readOnly = true)
    public List<String> findPendingCreditIds() {
        return pendingCreditRepository
                .findTop200ByStatusAndIsDeletedFalseOrderByCreatedAtAsc(PendingCreditStatus.PENDING)
                .stream().map(PendingInvestorCredit::getId).toList();
    }

    /**
     * Thử cộng lại một khoản hoàn trả bị lỗi (idempotent theo referenceId — nếu lần đầu thực ra đã
     * thành công ở payment-service thì lần này được skip, không cộng trùng). Thành công → COMPLETED
     * + thông báo nhà đầu tư. Một row một transaction để lỗi cái này không ảnh hưởng cái khác.
     */
    @Transactional
    public void reconcileCredit(String id) {
        PendingInvestorCredit pc = pendingCreditRepository.findById(id).orElse(null);
        if (pc == null || pc.isDeleted() || pc.getStatus() != PendingCreditStatus.PENDING) return;

        pc.setAttempts(pc.getAttempts() + 1);
        try {
            paymentServiceClient.creditRepayment(pc.getInvestorId(), pc.getAmount(),
                    pc.getDescription(), pc.getReferenceId());
            pc.setStatus(PendingCreditStatus.COMPLETED);
            pc.setLastError(null);
            pendingCreditRepository.save(pc);

            kafkaProducerService.publishCreditReconciled(InvestmentCreditReconciledEvent.builder()
                    .loanId(pc.getLoanId())
                    .loanCode(pc.getLoanCode())
                    .investorId(pc.getInvestorId())
                    .amount(pc.getAmount())
                    .build());
            log.info("Đối soát cộng bù thành công ref={} investor={} amount={} (lần {})",
                    pc.getReferenceId(), pc.getInvestorId(), pc.getAmount(), pc.getAttempts());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "unknown";
            pc.setLastError(msg.length() > 500 ? msg.substring(0, 500) : msg);
            pendingCreditRepository.save(pc);
            log.warn("Đối soát cộng bù vẫn lỗi ref={} (lần {}): {}",
                    pc.getReferenceId(), pc.getAttempts(), e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void evictLoanCaches(String loanId) {
        Cache byId = cacheManager.getCache(CacheConfig.CACHE_LOAN_BY_ID);
        if (byId != null) byId.evict(loanId);
        Cache list = cacheManager.getCache(CacheConfig.CACHE_LOANS);
        if (list != null) list.clear();
    }

    /** Lãi suất phạt của khoản = lateFeeRate% (mặc định 150%) của lãi suất — lấy theo sản phẩm. */
    private BigDecimal resolveLateFeeRate(LoanRequest loan) {
        if (loan.getProductId() == null) return DEFAULT_LATE_FEE_RATE;
        return loanProductService.findProductById(loan.getProductId())
                .map(p -> p.getLateFeeRate() != null ? p.getLateFeeRate() : DEFAULT_LATE_FEE_RATE)
                .orElse(DEFAULT_LATE_FEE_RATE);
    }

    /**
     * Phí phạt trả chậm = dư nợ kỳ × (lãi suất năm × lateFeeRate%) ÷ 365 × số ngày quá hạn.
     * Vd lãi 18%/năm, lateFeeRate 150% → lãi phạt 27%/năm trên dư nợ quá hạn. Làm tròn 2 chữ số.
     */
    private BigDecimal computeLateFee(BigDecimal remainingDue, BigDecimal baseAnnualRate,
                                      BigDecimal lateFeeRatePct, int dpd) {
        if (!lateFeeEnabled || dpd <= 0 || remainingDue == null || remainingDue.signum() <= 0
                || baseAnnualRate == null || baseAnnualRate.signum() <= 0
                || lateFeeRatePct == null || lateFeeRatePct.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal penaltyAnnualPct = baseAnnualRate.multiply(lateFeeRatePct)
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);
        BigDecimal dailyFraction = penaltyAnnualPct
                .divide(HUNDRED, 14, RoundingMode.HALF_UP)
                .divide(DAYS_PER_YEAR, 14, RoundingMode.HALF_UP);
        return remainingDue.multiply(dailyFraction).multiply(BigDecimal.valueOf(dpd))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private RepaymentScheduleResponse toResponse(RepaymentSchedule s) {
        return RepaymentScheduleResponse.builder()
                .periodNumber(s.getPeriodNumber())
                .dueDate(s.getDueDate())
                .principalDue(s.getPrincipalDue())
                .interestDue(s.getInterestDue())
                .totalDue(s.getTotalDue())
                .paidAmount(s.getPaidAmount())
                .lateFee(s.getLateFee())
                .lateFeeOutstanding(s.getLateFeeOutstanding())
                .totalOutstanding(s.getTotalOutstanding())
                .status(s.getStatus())
                .dpd(s.getDpd())
                .paidAt(s.getPaidAt())
                .build();
    }
}
