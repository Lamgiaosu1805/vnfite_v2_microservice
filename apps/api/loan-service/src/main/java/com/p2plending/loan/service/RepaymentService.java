package com.p2plending.loan.service;

import com.p2plending.loan.client.PaymentServiceClient;
import com.p2plending.loan.config.CacheConfig;
import com.p2plending.loan.domain.entity.EarlySettlement;
import com.p2plending.loan.domain.entity.InvestorDistributionLog;
import com.p2plending.loan.domain.entity.LoanOffer;
import com.p2plending.loan.domain.entity.LoanProduct;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.entity.PendingInvestorCredit;
import com.p2plending.loan.domain.entity.RepaymentAutoDebitAudit;
import com.p2plending.loan.domain.entity.RepaymentSchedule;
import com.p2plending.loan.domain.entity.RepaymentTransaction;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.OfferStatus;
import com.p2plending.loan.domain.enums.AutoDebitLoanResultStatus;
import com.p2plending.loan.domain.enums.PaymentChannel;
import com.p2plending.loan.domain.enums.PendingCreditStatus;
import com.p2plending.loan.domain.enums.ProductCategory;
import com.p2plending.loan.domain.enums.RepaymentMethod;
import com.p2plending.loan.domain.enums.RepaymentStatus;
import com.p2plending.loan.domain.repository.EarlySettlementRepository;
import com.p2plending.loan.domain.repository.InvestorDistributionLogRepository;
import com.p2plending.loan.domain.repository.LoanOfferRepository;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.domain.repository.PendingInvestorCreditRepository;
import com.p2plending.loan.domain.repository.RepaymentAutoDebitAuditRepository;
import com.p2plending.loan.domain.repository.RepaymentAutoDebitAuditItemRepository;
import com.p2plending.loan.domain.repository.RepaymentScheduleRepository;
import com.p2plending.loan.domain.repository.RepaymentTransactionRepository;
import com.p2plending.loan.dto.request.RecordPaymentRequest;
import com.p2plending.loan.dto.response.AutoDebitLoanResult;
import com.p2plending.loan.dto.response.EarlySettlementQuoteResponse;
import com.p2plending.loan.dto.response.RepaymentScheduleResponse;
import com.p2plending.loan.dto.response.RepaymentMonitoringResponse;
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

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepaymentService {

    private static final ZoneId TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Trạng thái khoản chấp nhận ghi nhận trả nợ (đã giải ngân + có lịch). */
    private static final Set<LoanStatus> PAYABLE_STATUSES =
            EnumSet.of(LoanStatus.DISBURSED, LoanStatus.REPAYING, LoanStatus.DEFAULTED);

    /**
     * Khoản đang trong vòng đời trả nợ — đối tượng quét DPD và thu nợ tự động.
     * Gồm cả DEFAULTED: khoản vỡ nợ vẫn phải tiếp tục cộng phí phạt theo ngày và
     * vẫn được auto-debit nếu người gọi vốn nạp tiền vào ví. Nếu loại DEFAULTED ra,
     * phí phạt sẽ đóng băng tại mốc default và ví có tiền cũng không thu được.
     */
    private static final Set<LoanStatus> SERVICING_STATUSES =
            EnumSet.of(LoanStatus.DISBURSED, LoanStatus.REPAYING, LoanStatus.DEFAULTED);

    private final RepaymentScheduleRepository      scheduleRepository;
    private final RepaymentTransactionRepository   transactionRepository;
    private final LoanRequestRepository            loanRequestRepository;
    private final LoanOfferRepository              loanOfferRepository;
    private final PendingInvestorCreditRepository  pendingCreditRepository;
    private final InvestorDistributionLogRepository distributionLogRepository;
    private final EarlySettlementRepository         earlySettlementRepository;
    private final RepaymentAutoDebitAuditRepository autoDebitAuditRepository;
    private final RepaymentAutoDebitAuditItemRepository autoDebitAuditItemRepository;
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

    /** Tỷ lệ thuế TNCN khấu trừ tại nguồn trên phần lãi và phí phạt của nhà đầu tư (mặc định 5%). */
    @Value("${app.tax.tncn-rate:0.05}")
    private BigDecimal tncnRate;

    @Transactional(readOnly = true)
    public RepaymentMonitoringResponse getMonitoring(int dueWithinDays) {
        int normalizedWindow = Math.max(0, Math.min(dueWithinDays, 30));
        LocalDate today = LocalDate.now(TZ);
        LocalDate dueCutoff = today.plusDays(normalizedWindow);
        List<RepaymentSchedule> schedules = scheduleRepository
                .findByStatusNotAndIsDeletedFalseOrderByDueDateAscPeriodNumberAsc(RepaymentStatus.PAID);

        Set<String> loanIds = schedules.stream()
                .map(RepaymentSchedule::getLoanId)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, LoanRequest> loansById = new HashMap<>();
        loanRequestRepository.findAllById(loanIds)
                .forEach(loan -> loansById.put(loan.getId(), loan));

        BigDecimal principalTotal = BigDecimal.ZERO;
        BigDecimal interestTotal = BigDecimal.ZERO;
        BigDecimal lateFeeTotal = BigDecimal.ZERO;
        long dueSoonInstallments = 0;
        long overdueInstallments = 0;
        Set<String> dueSoonBorrowers = new HashSet<>();
        Set<String> overdueBorrowers = new HashSet<>();
        List<RepaymentMonitoringResponse.RepaymentAttentionItem> attentionItems = new java.util.ArrayList<>();

        for (RepaymentSchedule schedule : schedules) {
            LoanRequest loan = loansById.get(schedule.getLoanId());
            if (loan == null || loan.isDeleted() || !PAYABLE_STATUSES.contains(loan.getStatus())) {
                continue;
            }

            // Phần còn lại lấy trực tiếp từ field tách sẵn (lãi/gốc đã trả riêng).
            BigDecimal interestOutstanding  = schedule.getInterestOutstanding();
            BigDecimal principalOutstanding = schedule.getPrincipalOutstanding();
            BigDecimal lateFeeOutstanding   = schedule.getLateFeeOutstanding();
            BigDecimal itemTotal = principalOutstanding.add(interestOutstanding).add(lateFeeOutstanding);

            principalTotal = principalTotal.add(principalOutstanding);
            interestTotal = interestTotal.add(interestOutstanding);
            lateFeeTotal = lateFeeTotal.add(lateFeeOutstanding);

            boolean overdue = schedule.getDueDate().isBefore(today);
            boolean dueSoon = !overdue && !schedule.getDueDate().isAfter(dueCutoff);
            if (overdue) {
                overdueInstallments++;
                overdueBorrowers.add(loan.getBorrowerId());
            } else if (dueSoon) {
                dueSoonInstallments++;
                dueSoonBorrowers.add(loan.getBorrowerId());
            }

            if (overdue || dueSoon) {
                int calculatedDpd = overdue
                        ? Math.toIntExact(ChronoUnit.DAYS.between(schedule.getDueDate(), today))
                        : 0;
                attentionItems.add(RepaymentMonitoringResponse.RepaymentAttentionItem.builder()
                        .loanId(loan.getId())
                        .loanCode(loan.getLoanCode())
                        .borrowerId(loan.getBorrowerId())
                        .periodNumber(schedule.getPeriodNumber())
                        .dueDate(schedule.getDueDate())
                        .dpd(calculatedDpd)
                        .status(overdue ? "OVERDUE" : "DUE_SOON")
                        .principalOutstanding(principalOutstanding)
                        .interestOutstanding(interestOutstanding)
                        .lateFeeOutstanding(lateFeeOutstanding)
                        .totalOutstanding(itemTotal)
                        .build());
            }
        }

        attentionItems.sort(java.util.Comparator
                .comparing((RepaymentMonitoringResponse.RepaymentAttentionItem item) ->
                        "OVERDUE".equals(item.getStatus()) ? 0 : 1)
                .thenComparing(RepaymentMonitoringResponse.RepaymentAttentionItem::getDueDate));

        return RepaymentMonitoringResponse.builder()
                .asOfDate(today)
                .dueWithinDays(normalizedWindow)
                .dueSoonInstallments(dueSoonInstallments)
                .dueSoonCustomers(dueSoonBorrowers.size())
                .overdueInstallments(overdueInstallments)
                .overdueCustomers(overdueBorrowers.size())
                .outstandingPrincipal(principalTotal)
                .outstandingInterest(interestTotal)
                .outstandingLateFee(lateFeeTotal)
                .totalOutstanding(principalTotal.add(interestTotal).add(lateFeeTotal))
                .attentionItems(attentionItems.stream().limit(50).toList())
                .build();
    }

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final BigDecimal DEFAULT_LATE_FEE_RATE = new BigDecimal("150.00");
    private static final BigDecimal DEFAULT_INTEREST_PENALTY_RATE = new BigDecimal("10.00");
    private static final BigDecimal DEFAULT_EARLY_SETTLEMENT_FEE_RATE = new BigDecimal("5.00");
    /** Ngưỡng miễn phí tất toán: đã dùng vốn ≥ 2/3 kỳ hạn thì miễn phí. */
    private static final BigDecimal DEFAULT_EARLY_SETTLEMENT_FREE_RATIO = new BigDecimal("0.6667");
    /** Mức phí tất toán trước hạn tối thiểu (VND) khi phí áp dụng. */
    private static final BigDecimal DEFAULT_EARLY_SETTLEMENT_MIN_FEE = new BigDecimal("500000");
    /** 100 × 365 — mẫu số lãi flat actual/365: gốc × rate(%) × ngày / 36500. */
    private static final BigDecimal RATE_BASE_36500 = new BigDecimal("36500");

    /**
     * Kết quả phân bổ một lần trả vào một kỳ theo thứ tự PHÍ → LÃI → GỐC.
     * Dùng để biết chính xác phần lãi/gốc/phí thực trả lần này → phân bổ đúng về nhà đầu tư + tính thuế.
     */
    private static final class Allocation {
        BigDecimal interestPenalty  = BigDecimal.ZERO; // phí phạt lãi quá hạn đã trả lần này
        BigDecimal principalPenalty = BigDecimal.ZERO; // phí phạt gốc quá hạn đã trả lần này
        BigDecimal interest         = BigDecimal.ZERO; // lãi đã trả lần này
        BigDecimal principal        = BigDecimal.ZERO; // gốc đã trả lần này

        BigDecimal penaltyTotal() { return interestPenalty.add(principalPenalty); }
        BigDecimal total()        { return penaltyTotal().add(interest).add(principal); }
    }

    /**
     * Áp một khoản tiền vào một kỳ theo thứ tự PHÍ → LÃI → GỐC.
     * Cập nhật các field đã-trả của kỳ (giữ bất biến tổng paidAmount/lateFeePaid) và
     * trả về breakdown để caller phân bổ đúng về nhà đầu tư.
     */
    private Allocation applyPayment(RepaymentSchedule s, BigDecimal amount) {
        BigDecimal remaining = money(amount);
        Allocation a = new Allocation();
        if (remaining.signum() <= 0) return a;

        // 1) Phí phạt lãi quá hạn
        BigDecimal ip = remaining.min(s.getInterestPenaltyOutstanding());
        if (ip.signum() > 0) { s.addInterestPenaltyPaid(ip); a.interestPenalty = ip; remaining = remaining.subtract(ip); }

        // 2) Phí phạt gốc quá hạn
        BigDecimal pp = remaining.min(s.getPrincipalPenaltyOutstanding());
        if (pp.signum() > 0) { s.addPrincipalPenaltyPaid(pp); a.principalPenalty = pp; remaining = remaining.subtract(pp); }

        // 3) Lãi
        BigDecimal it = remaining.min(s.getInterestOutstanding());
        if (it.signum() > 0) { s.addInterestPaid(it); a.interest = it; remaining = remaining.subtract(it); }

        // 4) Gốc
        BigDecimal pr = remaining.min(s.getPrincipalOutstanding());
        if (pr.signum() > 0) { s.addPrincipalPaid(pr); a.principal = pr; remaining = remaining.subtract(pr); }

        return a;
    }

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
        BigDecimal amount = money(request.getAmount());
        if (amount.signum() <= 0) {
            throw new InvalidLoanStateException("Số tiền trả phải lớn hơn 0");
        }
        BigDecimal remaining = amount;
        String firstTouchedScheduleId = null;

        for (RepaymentSchedule s : schedules) {
            if (remaining.signum() <= 0) break;
            if (s.isSettled()) continue;
            // Bỏ qua kỳ không còn gì phải trả (cả gốc+lãi lẫn phí phạt)
            if (s.getTotalOutstanding().signum() <= 0) continue;

            // Áp tiền theo thứ tự PHÍ → LÃI → GỐC
            Allocation alloc = applyPayment(s, remaining);
            remaining = remaining.subtract(alloc.total());
            if (alloc.total().signum() > 0 && firstTouchedScheduleId == null) {
                firstTouchedScheduleId = s.getId();
            }

            // Kỳ chỉ PAID khi cả phí phạt + lãi + gốc đều hết
            if (s.getTotalOutstanding().signum() <= 0) {
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
                .amount(amount)
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
                amount, loanId, loan.getStatus());

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

    // ── Tất toán trước hạn ────────────────────────────────────────

    /** Danh sách tất toán sớm — CMS sổ đối soát, phân trang, mới nhất trước. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<EarlySettlement> listEarlySettlements(int page, int size) {
        return earlySettlementRepository.findAllByIsDeletedFalseOrderBySettledAtDesc(
                PageRequest.of(page, size));
    }

    /** Báo giá tất toán trước hạn (không trừ tiền) — app/CMS hiển thị trước khi xác nhận. */
    @Transactional(readOnly = true)
    public EarlySettlementQuoteResponse quoteEarlySettlement(String loanId) {
        LoanRequest loan = loanRequestRepository.findById(loanId)
                .orElseThrow(() -> new LoanNotFoundException(loanId));
        boolean alreadySettled = earlySettlementRepository.existsByLoanIdAndIsDeletedFalse(loanId);
        List<RepaymentSchedule> schedules =
                scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(loanId);
        EarlySettlementCalc calc = computeEarlySettlement(loan, schedules, LocalDate.now(TZ));
        return toQuoteResponse(loan, calc, LocalDate.now(TZ), alreadySettled);
    }

    /**
     * Người gọi vốn tất toán trước hạn: trừ ví MỘT lần toàn bộ payoff (gốc còn lại + lãi tới
     * ngày tất toán + phí phạt quá hạn + phí tất toán 5%), miễn lãi các kỳ tương lai, phân bổ
     * gốc/lãi/phí phạt về nhà đầu tư (−TNCN), giữ phí 5% cho VNFITE, đánh khoản COMPLETED.
     *
     * @param borrowerId nếu khác null thì kiểm tra quyền sở hữu (app); null = vận hành CMS/OPS
     */
    @Transactional
    public EarlySettlementQuoteResponse earlySettle(String loanId, String borrowerId, String settledBy) {
        LoanRequest loan = loanRequestRepository.findById(loanId)
                .orElseThrow(() -> new LoanNotFoundException(loanId));
        if (borrowerId != null && !loan.getBorrowerId().equals(borrowerId)) {
            throw new InvalidLoanStateException("Bạn không có quyền tất toán khoản gọi vốn này");
        }
        if (!PAYABLE_STATUSES.contains(loan.getStatus())) {
            throw new InvalidLoanStateException(
                    "Khoản gọi vốn %s không ở trạng thái cho tất toán (status: %s)"
                    .formatted(loan.getLoanCode(), loan.getStatus()));
        }
        if (earlySettlementRepository.existsByLoanIdAndIsDeletedFalse(loanId)) {
            throw new InvalidLoanStateException(
                    "Khoản gọi vốn %s đã được tất toán trước hạn".formatted(loan.getLoanCode()));
        }

        List<RepaymentSchedule> schedules =
                scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(loanId);
        LocalDate today = LocalDate.now(TZ);
        EarlySettlementCalc calc = computeEarlySettlement(loan, schedules, today);
        if (calc.totalPayoff.signum() <= 0) {
            throw new InvalidLoanStateException(
                    "Khoản gọi vốn %s không còn dư nợ để tất toán".formatted(loan.getLoanCode()));
        }

        LocalDateTime now = LocalDateTime.now(TZ);
        String borrowerRef = "EARLY-OUT-" + loanId;

        // 1) Trừ ví borrower MỘT lần cho toàn bộ payoff (fail-closed: ví thiếu → ném → rollback)
        paymentServiceClient.debitRepayment(loan.getBorrowerId(), borrowerWalletOwnerType(loan), calc.totalPayoff,
                "Tất toán trước hạn khoản %s".formatted(loan.getLoanCode()), borrowerRef);

        // 2) Áp vào từng kỳ chưa trả + phân bổ NĐT; miễn lãi các kỳ tương lai
        List<LoanRepaidEvent.InvestorPayout> payouts = new ArrayList<>();
        for (PeriodPayoff pp : calc.periods) {
            RepaymentSchedule s = pp.schedule;
            if (pp.principal.signum() > 0) s.addPrincipalPaid(pp.principal);
            if (pp.interest.signum() > 0)  s.addInterestPaid(pp.interest);
            BigDecimal intPenPay  = s.getInterestPenaltyOutstanding();
            BigDecimal prinPenPay = s.getPrincipalPenaltyOutstanding();
            if (intPenPay.signum() > 0)  s.addInterestPenaltyPaid(intPenPay);
            if (prinPenPay.signum() > 0) s.addPrincipalPenaltyPaid(prinPenPay);

            // Miễn phần lãi tương lai chưa phát sinh: hạ interestDue về đúng mức đã trả tới ngày tất toán.
            s.setInterestDue(money(s.getInterestPaid()));
            s.setTotalDue(money(money(s.getPrincipalDue()).add(money(s.getInterestDue()))));
            s.setStatus(RepaymentStatus.PAID);
            s.setPaidAt(now);
            s.setDpd(0);
            scheduleRepository.save(s);

            BigDecimal periodPaid = pp.principal.add(pp.interest).add(intPenPay).add(prinPenPay);
            if (periodPaid.signum() <= 0) continue;
            RepaymentTransaction txn = transactionRepository.save(RepaymentTransaction.builder()
                    .loanId(loanId)
                    .scheduleId(s.getId())
                    .amount(money(periodPaid))
                    .paidAt(now)
                    .channel(PaymentChannel.WALLET)
                    .externalRef(borrowerRef + "-P" + s.getPeriodNumber())
                    .recordedBy(settledBy)
                    .note("Tất toán trước hạn kỳ %d".formatted(s.getPeriodNumber()))
                    .build());
            payouts.addAll(distributeToInvestors(loan, s, pp.principal, pp.interest,
                    intPenPay.add(prinPenPay), txn.getId(),
                    "EARLY-IN-" + loanId + "-P" + s.getPeriodNumber()));
        }

        // 3) Ghi sổ phí tất toán 5% (đọng tại tài khoản tổng VNFITE — không transfer)
        earlySettlementRepository.save(EarlySettlement.builder()
                .loanId(loanId)
                .loanCode(loan.getLoanCode())
                .borrowerId(loan.getBorrowerId())
                .principalSettled(calc.remainingPrincipal)
                .interestToDate(calc.interestToDate)
                .penaltyPaid(calc.penaltyOutstanding)
                .settlementFee(calc.settlementFee)
                .settlementFeeRate(calc.settlementFeeRate)
                .totalPaid(calc.totalPayoff)
                .settledAt(now)
                .settledBy(settledBy)
                .build());

        // 4) COMPLETED + cache + event
        loan.setStatus(LoanStatus.COMPLETED);
        loanRequestRepository.save(loan);
        evictLoanCaches(loanId);
        kafkaProducerService.publishLoanRepaid(LoanRepaidEvent.builder()
                .loanId(loanId)
                .loanCode(loan.getLoanCode())
                .borrowerId(loan.getBorrowerId())
                .periodNumber(0)
                .totalPeriods(schedules.size())
                .amountPaid(calc.totalPayoff)
                .periodTotalDue(calc.totalPayoff)
                .partial(false)
                .loanCompleted(true)
                .channel("EARLY_SETTLEMENT")
                .paidAt(now)
                .investorPayouts(payouts)
                .build());

        log.info("Tất toán trước hạn loan {} by {}: gốc={} lãiTớiNgày={} phíPhạt={} phíTấtToán={} tổng={}",
                loanId, settledBy, calc.remainingPrincipal, calc.interestToDate, calc.penaltyOutstanding,
                calc.settlementFee, calc.totalPayoff);
        return toQuoteResponse(loan, calc, today, true);
    }

    /** Tính các cấu phần tất toán trước hạn tại ngày {@code today} (không chạm DB ghi). */
    private EarlySettlementCalc computeEarlySettlement(LoanRequest loan,
                                                       List<RepaymentSchedule> schedules, LocalDate today) {
        BigDecimal feeRate    = resolveEarlySettlementFeeRate(loan);
        BigDecimal annualRate = loan.getInterestRate() != null ? loan.getInterestRate() : BigDecimal.ZERO;
        BigDecimal principal  = loan.getAmount() != null ? loan.getAmount() : BigDecimal.ZERO;
        LocalDate  loanStart  = loanStartDate(loan);

        EarlySettlementCalc calc = new EarlySettlementCalc();
        calc.settlementFeeRate = feeRate;

        for (int idx = 0; idx < schedules.size(); idx++) {
            RepaymentSchedule s = schedules.get(idx);
            if (s.isSettled()) continue;

            BigDecimal principalOut = s.getPrincipalOutstanding();
            BigDecimal penaltyOut   = s.getLateFeeOutstanding();

            BigDecimal interestOwed;
            if (!s.getDueDate().isAfter(today)) {
                // Kỳ đã đến hạn / quá hạn → lãi phát sinh đủ
                interestOwed = s.getInterestOutstanding();
            } else {
                LocalDate start = (idx == 0) ? loanStart : schedules.get(idx - 1).getDueDate();
                if (start.isBefore(today)) {
                    // Kỳ đang chạy → pro-rate lãi flat trên gốc ban đầu từ đầu kỳ tới hôm nay
                    long elapsed = ChronoUnit.DAYS.between(start, today);
                    BigDecimal accrued = money(principal.multiply(annualRate)
                            .multiply(BigDecimal.valueOf(elapsed))
                            .divide(RATE_BASE_36500, 10, RoundingMode.HALF_UP));
                    accrued = accrued.min(money(s.getInterestDue()));
                    interestOwed = accrued.subtract(money(s.getInterestPaid())).max(BigDecimal.ZERO);
                } else {
                    // Kỳ hoàn toàn tương lai → miễn lãi
                    interestOwed = BigDecimal.ZERO;
                }
            }

            calc.remainingPrincipal = calc.remainingPrincipal.add(principalOut);
            calc.interestToDate     = calc.interestToDate.add(interestOwed);
            calc.penaltyOutstanding = calc.penaltyOutstanding.add(penaltyOut);
            calc.periods.add(new PeriodPayoff(s, principalOut, interestOwed, penaltyOut));
        }

        // Phí tất toán theo hợp đồng (Phụ Lục 01):
        //   • Đã dùng vốn ≥ ngưỡng (mặc định 2/3) kỳ hạn → MIỄN PHÍ
        //   • Ngược lại → feeRate% × gốc còn lại, nhưng không thấp hơn mức sàn (mặc định 500.000đ)
        LocalDate lastDueDate = schedules.isEmpty()
                ? today : schedules.get(schedules.size() - 1).getDueDate();
        long termDays = ChronoUnit.DAYS.between(loanStart, lastDueDate);
        long usedDays = ChronoUnit.DAYS.between(loanStart, today);
        BigDecimal freeRatio = resolveEarlySettlementFreeRatio(loan);
        boolean feeWaived = termDays > 0 && BigDecimal.valueOf(usedDays)
                .divide(BigDecimal.valueOf(termDays), 6, RoundingMode.HALF_UP)
                .compareTo(freeRatio) >= 0;

        if (feeWaived || calc.remainingPrincipal.signum() <= 0) {
            calc.settlementFeeRate = BigDecimal.ZERO;
            calc.settlementFee     = BigDecimal.ZERO;
        } else {
            BigDecimal fee = money(calc.remainingPrincipal.multiply(feeRate)
                    .divide(HUNDRED, 0, RoundingMode.HALF_UP));
            calc.settlementFee = money(fee.max(resolveEarlySettlementMinFee(loan)));
        }
        calc.totalPayoff = money(calc.remainingPrincipal.add(calc.interestToDate)
                .add(calc.penaltyOutstanding).add(calc.settlementFee));
        return calc;
    }

    /** Tỷ lệ phí tất toán trước hạn (% gốc còn lại, mặc định 5%) — theo sản phẩm. */
    private BigDecimal resolveEarlySettlementFeeRate(LoanRequest loan) {
        if (loan.getProductId() == null) return DEFAULT_EARLY_SETTLEMENT_FEE_RATE;
        return loanProductService.findProductById(loan.getProductId())
                .map(p -> p.getEarlySettlementFeeRate() != null
                        ? p.getEarlySettlementFeeRate() : DEFAULT_EARLY_SETTLEMENT_FEE_RATE)
                .orElse(DEFAULT_EARLY_SETTLEMENT_FEE_RATE);
    }

    /** Ngưỡng miễn phí tất toán (tỷ lệ kỳ hạn đã dùng, mặc định 2/3) — theo sản phẩm. */
    private BigDecimal resolveEarlySettlementFreeRatio(LoanRequest loan) {
        if (loan.getProductId() == null) return DEFAULT_EARLY_SETTLEMENT_FREE_RATIO;
        return loanProductService.findProductById(loan.getProductId())
                .map(p -> p.getEarlySettlementFreeRatio() != null
                        ? p.getEarlySettlementFreeRatio() : DEFAULT_EARLY_SETTLEMENT_FREE_RATIO)
                .orElse(DEFAULT_EARLY_SETTLEMENT_FREE_RATIO);
    }

    /** Mức phí tất toán tối thiểu (VND, mặc định 500.000) — theo sản phẩm. */
    private BigDecimal resolveEarlySettlementMinFee(LoanRequest loan) {
        if (loan.getProductId() == null) return DEFAULT_EARLY_SETTLEMENT_MIN_FEE;
        return loanProductService.findProductById(loan.getProductId())
                .map(p -> p.getEarlySettlementMinFee() != null
                        ? p.getEarlySettlementMinFee() : DEFAULT_EARLY_SETTLEMENT_MIN_FEE)
                .orElse(DEFAULT_EARLY_SETTLEMENT_MIN_FEE);
    }

    /** Mốc bắt đầu kỳ 1 = ngày giải ngân (fallback fundedAt / hôm nay). */
    private LocalDate loanStartDate(LoanRequest loan) {
        LocalDateTime t = loan.getDisbursedAt() != null ? loan.getDisbursedAt() : loan.getFundedAt();
        return t != null ? t.toLocalDate() : LocalDate.now(TZ);
    }

    private EarlySettlementQuoteResponse toQuoteResponse(LoanRequest loan, EarlySettlementCalc calc,
                                                         LocalDate asOf, boolean settled) {
        return EarlySettlementQuoteResponse.builder()
                .loanId(loan.getId())
                .loanCode(loan.getLoanCode())
                .asOfDate(asOf)
                .remainingPrincipal(money(calc.remainingPrincipal))
                .interestToDate(money(calc.interestToDate))
                .penaltyOutstanding(money(calc.penaltyOutstanding))
                .settlementFeeRate(calc.settlementFeeRate)
                .settlementFee(money(calc.settlementFee))
                .totalPayoff(money(calc.totalPayoff))
                .settled(settled)
                .build();
    }

    /** Tổng hợp cấu phần tất toán + breakdown từng kỳ để áp tiền. */
    private static final class EarlySettlementCalc {
        BigDecimal remainingPrincipal = BigDecimal.ZERO;
        BigDecimal interestToDate     = BigDecimal.ZERO;
        BigDecimal penaltyOutstanding = BigDecimal.ZERO;
        BigDecimal settlementFee      = BigDecimal.ZERO;
        BigDecimal settlementFeeRate  = BigDecimal.ZERO;
        BigDecimal totalPayoff        = BigDecimal.ZERO;
        final List<PeriodPayoff> periods = new ArrayList<>();
    }

    /** Phần phải trả của một kỳ khi tất toán. */
    private static final class PeriodPayoff {
        final RepaymentSchedule schedule;
        final BigDecimal principal;
        final BigDecimal interest;
        final BigDecimal penalty;
        PeriodPayoff(RepaymentSchedule schedule, BigDecimal principal, BigDecimal interest, BigDecimal penalty) {
            this.schedule = schedule; this.principal = principal; this.interest = interest; this.penalty = penalty;
        }
    }

    /** ID các khoản đang trả nợ — scheduler đọc riêng để auto-debit từng khoản trong transaction độc lập. */
    @Transactional(readOnly = true)
    public List<String> findAutoDebitLoanIds() {
        return loanRequestRepository.findByStatusInAndIsDeletedFalse(SERVICING_STATUSES)
                .stream().map(LoanRequest::getId).toList();
    }

    /** Nhắc trước hạn N ngày để người gọi vốn chuẩn bị số dư. Không trừ tiền. */
    @Transactional(readOnly = true)
    public int publishUpcomingDueReminders(int daysAhead) {
        int normalizedDays = Math.max(1, Math.min(daysAhead, 7));
        LocalDate targetDate = LocalDate.now(TZ).plusDays(normalizedDays);
        List<RepaymentSchedule> schedules = scheduleRepository
                .findByStatusNotAndIsDeletedFalseOrderByDueDateAscPeriodNumberAsc(RepaymentStatus.PAID);
        Set<String> loanIds = schedules.stream()
                .filter(s -> targetDate.equals(s.getDueDate()))
                .map(RepaymentSchedule::getLoanId)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, LoanRequest> loansById = new HashMap<>();
        loanRequestRepository.findAllById(loanIds)
                .forEach(loan -> loansById.put(loan.getId(), loan));

        int sent = 0;
        for (RepaymentSchedule schedule : schedules) {
            if (!targetDate.equals(schedule.getDueDate())) continue;
            if (schedule.getTotalOutstanding().signum() <= 0) continue;
            LoanRequest loan = loansById.get(schedule.getLoanId());
            if (loan == null || loan.isDeleted() || !PAYABLE_STATUSES.contains(loan.getStatus())) continue;

            List<RepaymentSchedule> loanSchedules =
                    scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(loan.getId());
            kafkaProducerService.publishRepaymentDueReminder(RepaymentDueReminderEvent.builder()
                    .loanId(loan.getId())
                    .loanCode(loan.getLoanCode())
                    .borrowerId(loan.getBorrowerId())
                    .periodNumber(schedule.getPeriodNumber())
                    .totalPeriods(loanSchedules.size())
                    .amountDue(schedule.getTotalOutstanding())
                    .dueDate(schedule.getDueDate())
                    .daysUntilDue(normalizedDays)
                    .dpd(0)
                    .build());
            sent++;
        }
        log.info("Published upcoming repayment reminders: daysAhead={} targetDate={} sent={}",
                normalizedDays, targetDate, sent);
        return sent;
    }

    /**
     * Scheduler: tự động trừ ví người gọi vốn cho kỳ đến hạn sớm nhất (dueDate ≤ hôm nay).
     * Số dư đủ → trả đủ kỳ; số dư thiếu → trả một phần; không có số dư → bỏ qua (DPD đánh OVERDUE sau).
     * Mỗi khoản một transaction riêng để một khoản lỗi không ảnh hưởng khoản khác.
     */
    @Transactional
    public AutoDebitLoanResult autoDebitLoan(String loanId) {
        LoanRequest loan = loanRequestRepository.findById(loanId).orElse(null);
        if (loan == null || !SERVICING_STATUSES.contains(loan.getStatus())) {
            return AutoDebitLoanResult.builder()
                    .loanId(loanId)
                    .status(AutoDebitLoanResultStatus.NO_DUE)
                    .amountCollected(BigDecimal.ZERO)
                    .message("Khoản không còn trong trạng thái thu nợ")
                    .build();
        }

        List<RepaymentSchedule> schedules =
                scheduleRepository.findByLoanIdAndIsDeletedFalseOrderByPeriodNumberAsc(loanId);
        LocalDate today = LocalDate.now(TZ);
        RepaymentSchedule due = firstUnpaid(schedules)
                .filter(s -> !s.getDueDate().isAfter(today))   // chỉ trừ khi đã tới hạn
                .orElse(null);
        if (due == null) {
            return AutoDebitLoanResult.builder()
                    .loanId(loan.getId())
                    .loanCode(loan.getLoanCode())
                    .borrowerId(loan.getBorrowerId())
                    .status(AutoDebitLoanResultStatus.NO_DUE)
                    .amountCollected(BigDecimal.ZERO)
                    .message("Chưa có kỳ đến hạn")
                    .build();
        }

        BigDecimal available;
        try {
            available = paymentServiceClient.getAvailableBalance(loan.getBorrowerId(), borrowerWalletOwnerType(loan));
        } catch (Exception e) {
            log.warn("Auto-debit loan {}: không lấy được số dư ví borrower {}: {}",
                    loanId, loan.getBorrowerId(), e.getMessage());
            return AutoDebitLoanResult.builder()
                    .loanId(loan.getId())
                    .loanCode(loan.getLoanCode())
                    .borrowerId(loan.getBorrowerId())
                    .status(AutoDebitLoanResultStatus.BALANCE_ERROR)
                    .amountCollected(BigDecimal.ZERO)
                    .message("Không lấy được số dư ví")
                    .build();
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
                    .daysUntilDue(0)
                    .dpd(due.getDpd() != null ? due.getDpd() : 0)
                    .build());
            return AutoDebitLoanResult.builder()
                    .loanId(loan.getId())
                    .loanCode(loan.getLoanCode())
                    .borrowerId(loan.getBorrowerId())
                    .status(AutoDebitLoanResultStatus.NO_BALANCE)
                    .amountCollected(BigDecimal.ZERO)
                    .message("Ví không có số dư khả dụng")
                    .build();
        }

        BigDecimal payAmount = money(available.min(due.getTotalOutstanding()));
        if (payAmount.signum() <= 0) {
            return AutoDebitLoanResult.builder()
                    .loanId(loan.getId())
                    .loanCode(loan.getLoanCode())
                    .borrowerId(loan.getBorrowerId())
                    .status(AutoDebitLoanResultStatus.NO_BALANCE)
                    .amountCollected(BigDecimal.ZERO)
                    .message("Số dư khả dụng sau làm tròn không đủ để thu")
                    .build();
        }
        doSettlePeriod(loan, due, payAmount, PaymentChannel.AUTO_DEBIT, null, schedules.size());
        boolean settledFull = due.getTotalOutstanding().signum() <= 0;
        return AutoDebitLoanResult.builder()
                .loanId(loan.getId())
                .loanCode(loan.getLoanCode())
                .borrowerId(loan.getBorrowerId())
                .status(settledFull
                        ? AutoDebitLoanResultStatus.SETTLED_FULL
                        : AutoDebitLoanResultStatus.SETTLED_PARTIAL)
                .amountCollected(payAmount)
                .message(settledFull ? "Đã thu đủ kỳ đến hạn" : "Đã thu một phần kỳ đến hạn")
                .build();
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
        payAmount = money(payAmount);
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
        paymentServiceClient.debitRepayment(loan.getBorrowerId(), borrowerWalletOwnerType(loan), payAmount,
                "Trả nợ kỳ %d khoản %s".formatted(period.getPeriodNumber(), loan.getLoanCode()),
                borrowerRef);

        // 2) Áp tiền vào kỳ theo thứ tự PHÍ → LÃI → GỐC
        Allocation alloc = applyPayment(period, payAmount);

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
        RepaymentTransaction txn = transactionRepository.save(RepaymentTransaction.builder()
                .loanId(loan.getId())
                .scheduleId(period.getId())
                .amount(payAmount)
                .paidAt(paidAt)
                .channel(channel)
                .externalRef(borrowerRef)
                .recordedBy(recordedBy)
                .note("Trả nợ kỳ %d".formatted(period.getPeriodNumber()))
                .build());

        // 4) Phân bổ pro-rata về ví nhà đầu tư (best-effort, idempotent), tính thuế TNCN
        List<LoanRepaidEvent.InvestorPayout> payouts =
                distributeToInvestors(loan, period, alloc.principal, alloc.interest, alloc.penaltyTotal(),
                        txn.getId(), "REPAY-IN-" + baseRef);

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
     * Chia tiền trả nợ cho nhà đầu tư theo tỉ lệ số tiền đã đầu tư (offer ACCEPTED).
     * Khấu trừ thuế TNCN 5% trên phần lãi và phí phạt; phần gốc không chịu thuế.
     * Dồn phần dư làm tròn vào nhà đầu tư cuối để tổng khớp tuyệt đối.
     * Cộng ví best-effort: lỗi từng người được log để đối soát, không làm hỏng giao dịch.
     *
     * @param period             kỳ trả nợ
     * @param principalPaid      phần gốc thực trả lần này
     * @param interestPaid       phần lãi thực trả lần này
     * @param penaltyPaid        phần phí phạt (lãi+gốc quá hạn) thực trả lần này
     * @param repaymentTransactionId ID giao dịch trả nợ vừa lưu
     * @param creditRefBase      prefix cho credit ref idempotent
     */
    private List<LoanRepaidEvent.InvestorPayout> distributeToInvestors(
            LoanRequest loan, RepaymentSchedule period,
            BigDecimal principalPaid, BigDecimal interestPaid, BigDecimal penaltyPaid,
            String repaymentTransactionId, String creditRefBase) {

        List<LoanRepaidEvent.InvestorPayout> payouts = new ArrayList<>();
        BigDecimal principalTot = money(principalPaid);
        BigDecimal interestTot  = money(interestPaid);
        BigDecimal penaltyTot   = money(penaltyPaid);
        BigDecimal payAmount    = principalTot.add(interestTot).add(penaltyTot);
        if (payAmount.signum() <= 0) return payouts;

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

        BigDecimal effectiveTaxRate = (tncnRate != null && tncnRate.signum() > 0) ? tncnRate : BigDecimal.ZERO;

        BigDecimal distPrincipal = BigDecimal.ZERO;
        BigDecimal distInterest  = BigDecimal.ZERO;
        BigDecimal distPenalty   = BigDecimal.ZERO;
        LocalDateTime distributedAt = LocalDateTime.now(TZ);

        for (int i = 0; i < offers.size(); i++) {
            LoanOffer offer = offers.get(i);
            boolean isLast = (i == offers.size() - 1);

            // Pro-rata từng phần (gốc/lãi/phí); dồn phần dư làm tròn vào investor cuối
            BigDecimal principalShare, interestShare, penaltyShare;
            if (isLast) {
                principalShare = principalTot.subtract(distPrincipal);
                interestShare  = interestTot.subtract(distInterest);
                penaltyShare   = penaltyTot.subtract(distPenalty);
            } else {
                principalShare = principalTot.multiply(offer.getAmount()).divide(totalFunded, 0, RoundingMode.DOWN);
                interestShare  = interestTot.multiply(offer.getAmount()).divide(totalFunded, 0, RoundingMode.DOWN);
                penaltyShare   = penaltyTot.multiply(offer.getAmount()).divide(totalFunded, 0, RoundingMode.DOWN);
            }
            distPrincipal = distPrincipal.add(principalShare);
            distInterest  = distInterest.add(interestShare);
            distPenalty   = distPenalty.add(penaltyShare);

            BigDecimal share = principalShare.add(interestShare).add(penaltyShare);
            if (share.signum() <= 0) continue;

            // Nhà đầu tư pháp nhân (đầu tư tư cách DN) TỰ quyết toán thuế TNDN → VNFITE KHÔNG khấu trừ
            // tại nguồn, cộng đủ gốc+lãi+phí vào ví DN. Nhà đầu tư cá nhân: khấu trừ TNCN trên
            // (lãi + phí phạt); gốc không chịu thuế; tiền thuế VNFITE giữ lại nộp thay, hạch toán ở log.
            boolean businessInvestor = "BUSINESS".equals(offer.getOwnerType());
            BigDecimal offerTaxRate = businessInvestor ? BigDecimal.ZERO : effectiveTaxRate;
            BigDecimal taxAmount = money(interestShare.add(penaltyShare).multiply(offerTaxRate));
            BigDecimal netAmount = share.subtract(taxAmount);
            String creditDesc = businessInvestor
                    ? "Nhận lợi nhuận khoản %s".formatted(loan.getLoanCode())
                    : "Nhận lợi nhuận khoản %s (sau thuế TNCN)".formatted(loan.getLoanCode());

            String creditRef = creditRefBase + "-" + offer.getId();

            try {
                paymentServiceClient.creditRepayment(offer.getInvestorId(), offer.getOwnerType(), netAmount,
                        creditDesc, creditRef);
                payouts.add(LoanRepaidEvent.InvestorPayout.builder()
                        .investorId(offer.getInvestorId())
                        .amount(netAmount)
                        .build());
            } catch (Exception e) {
                log.error("ĐỐI SOÁT: phân bổ thất bại loan={} investor={} offer={} gross={} net={} tax={} ref={}: {}",
                        loan.getId(), offer.getInvestorId(), offer.getId(), share, netAmount, taxAmount, creditRef, e.getMessage());
                if (!pendingCreditRepository.existsByReferenceId(creditRef)) {
                    pendingCreditRepository.save(PendingInvestorCredit.builder()
                            .loanId(loan.getId())
                            .loanCode(loan.getLoanCode())
                            .investorId(offer.getInvestorId())
                            .offerId(offer.getId())
                            .ownerType(offer.getOwnerType())
                            .amount(netAmount)
                            .referenceId(creditRef)
                            .description(creditDesc)
                            .build());
                }
            }

            // Ghi log phân bổ (gốc/lãi/phí phạt/thuế/net) — kế toán dùng để đối soát
            try {
                distributionLogRepository.save(InvestorDistributionLog.builder()
                        .repaymentTransactionId(repaymentTransactionId)
                        .loanId(loan.getId())
                        .loanCode(loan.getLoanCode())
                        .scheduleId(period.getId())
                        .offerId(offer.getId())
                        .investorId(offer.getInvestorId())
                        .grossAmount(share)
                        .principalAmount(principalShare)
                        .interestAmount(interestShare)
                        .lateFeeAmount(penaltyShare)
                        .taxRate(offerTaxRate)
                        .taxAmount(taxAmount)
                        .netAmount(netAmount)
                        .creditRef(creditRef)
                        .distributedAt(distributedAt)
                        .build());
            } catch (Exception ex) {
                log.error("Ghi distribution log thất bại loan={} investor={}: {}", loan.getId(), offer.getInvestorId(), ex.getMessage());
            }
        }
        return payouts;
    }

    /** Lấy lịch sử quét auto-debit (mới nhất trước) — dùng cho CMS. */
    @Transactional(readOnly = true)
    public List<com.p2plending.loan.dto.response.AutoDebitSweepResponse> getAutoDebitAuditList(int limit) {
        return autoDebitAuditRepository.findAll(
                PageRequest.of(0, Math.min(limit, 500), Sort.by("startedAt").descending()))
                .getContent().stream()
                .map(a -> com.p2plending.loan.dto.response.AutoDebitSweepResponse.builder()
                        .auditId(a.getId())
                        .triggerSource(a.getTriggerSource())
                        .triggeredBy(a.getTriggeredBy())
                        .startedAt(a.getStartedAt())
                        .finishedAt(a.getFinishedAt())
                        .scannedLoans(a.getScannedLoans())
                        .dueLoans(a.getDueLoans())
                        .settledFull(a.getSettledFull())
                        .settledPartial(a.getSettledPartial())
                        .noBalance(a.getNoBalance())
                        .balanceError(a.getBalanceError())
                        .noDue(a.getNoDue())
                        .failed(a.getFailed())
                        .amountCollected(a.getAmountCollected())
                        .errorSummary(a.getErrorSummary())
                        .build())
                .toList();
    }

    /** Chi tiết từng khoản trong một lần quét auto-debit — dùng cho CMS. */
    @Transactional(readOnly = true)
    public List<com.p2plending.loan.domain.entity.RepaymentAutoDebitAuditItem> getAutoDebitAuditItems(String auditId) {
        return autoDebitAuditItemRepository.findByAuditIdAndIsDeletedFalseOrderByCreatedAtAsc(auditId);
    }

    /** Lấy log phân bổ nhà đầu tư có phân trang và lọc theo loanId/investorId — dùng cho CMS. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<InvestorDistributionLog> getDistributionLog(
            String loanId, String investorId, int page, int size) {
        String ln = (loanId != null && loanId.isBlank()) ? null : loanId;
        String iv = (investorId != null && investorId.isBlank()) ? null : investorId;
        return distributionLogRepository.findFiltered(
                ln, iv, PageRequest.of(page, Math.min(size, 100)));
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
            BigDecimal principalPenaltyRate = resolveLateFeeRate(loan);       // 150% × lãi suất
            BigDecimal interestPenaltyRate  = resolveInterestPenaltyRate(loan); // 10%/năm

            for (RepaymentSchedule s : schedules) {
                if (s.isSettled()) continue;
                if (s.getDueDate().isBefore(today)) {
                    int dpd = (int) ChronoUnit.DAYS.between(s.getDueDate(), today);
                    // Số ngày tăng thêm từ lần sweep trước — thường = 1; > 1 nếu sweep bị skip một ngày.
                    // Cộng dồn phí theo delta trên phần lãi/gốc CÒN LẠI để phí tích lũy chính xác ngay cả
                    // khi borrower trả bớt một phần giữa các lần sweep. Chỉ cộng (không tính lại từ đầu),
                    // nên phí luôn ≥ phần đã trả, không cần guard đóng băng.
                    int prevDpd = s.getDpd() != null ? s.getDpd() : 0;
                    int deltaDpd = dpd - prevDpd;
                    s.setDpd(dpd);
                    s.setStatus(RepaymentStatus.OVERDUE);
                    if (deltaDpd > 0) {
                        // Phạt lãi quá hạn: lãi chưa trả × 10%/năm × ngày/365
                        BigDecimal addIntPen = computeInterestPenalty(
                                s.getInterestOutstanding(), interestPenaltyRate, deltaDpd);
                        // Phạt gốc quá hạn: gốc chưa trả × (150%×lãi suất)/năm × ngày/365
                        BigDecimal addPrinPen = computePrincipalPenalty(
                                s.getPrincipalOutstanding(), loan.getInterestRate(), principalPenaltyRate, deltaDpd);
                        if (addIntPen.signum() > 0)  s.addInterestPenalty(addIntPen);
                        if (addPrinPen.signum() > 0) s.addPrincipalPenalty(addPrinPen);
                    }
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
            paymentServiceClient.creditRepayment(pc.getInvestorId(), pc.getOwnerType(), pc.getAmount(),
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

    /** Hệ số phạt GỐC quá hạn = lateFeeRate% (mặc định 150%) của lãi suất — theo sản phẩm. */
    private BigDecimal resolveLateFeeRate(LoanRequest loan) {
        if (loan.getProductId() == null) return DEFAULT_LATE_FEE_RATE;
        return loanProductService.findProductById(loan.getProductId())
                .map(p -> p.getLateFeeRate() != null ? p.getLateFeeRate() : DEFAULT_LATE_FEE_RATE)
                .orElse(DEFAULT_LATE_FEE_RATE);
    }

    /** Lãi suất phạt LÃI quá hạn (%/năm, mặc định 10%) — theo sản phẩm. */
    private BigDecimal resolveInterestPenaltyRate(LoanRequest loan) {
        if (loan.getProductId() == null) return DEFAULT_INTEREST_PENALTY_RATE;
        return loanProductService.findProductById(loan.getProductId())
                .map(p -> p.getInterestPenaltyRate() != null ? p.getInterestPenaltyRate() : DEFAULT_INTEREST_PENALTY_RATE)
                .orElse(DEFAULT_INTEREST_PENALTY_RATE);
    }

    /**
     * Phí phạt GỐC quá hạn = gốc chưa trả × (lãi suất năm × lateFeeRate%) ÷ 365 × số ngày.
     * Vd lãi 18%/năm, lateFeeRate 150% → 27%/năm trên gốc quá hạn.
     */
    private BigDecimal computePrincipalPenalty(BigDecimal principalOutstanding, BigDecimal baseAnnualRate,
                                               BigDecimal lateFeeRatePct, int dpd) {
        if (!lateFeeEnabled || dpd <= 0 || principalOutstanding == null || principalOutstanding.signum() <= 0
                || baseAnnualRate == null || baseAnnualRate.signum() <= 0
                || lateFeeRatePct == null || lateFeeRatePct.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal penaltyAnnualPct = baseAnnualRate.multiply(lateFeeRatePct)
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);
        return money(applyDailyRate(principalOutstanding, penaltyAnnualPct, dpd));
    }

    /**
     * Phí phạt LÃI quá hạn = lãi chưa trả × penaltyRate%/năm ÷ 365 × số ngày.
     * Vd penaltyRate 10%/năm trên phần lãi quá hạn.
     */
    private BigDecimal computeInterestPenalty(BigDecimal interestOutstanding, BigDecimal penaltyAnnualPct, int dpd) {
        if (!lateFeeEnabled || dpd <= 0 || interestOutstanding == null || interestOutstanding.signum() <= 0
                || penaltyAnnualPct == null || penaltyAnnualPct.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return money(applyDailyRate(interestOutstanding, penaltyAnnualPct, dpd));
    }

    /** base × (annualPct% ÷ 365) × dpd — chia phân số ngày ở scale cao rồi nhân; làm tròn ở caller. */
    private BigDecimal applyDailyRate(BigDecimal base, BigDecimal annualPct, int dpd) {
        BigDecimal dailyFraction = annualPct
                .divide(HUNDRED, 14, RoundingMode.HALF_UP)
                .divide(DAYS_PER_YEAR, 14, RoundingMode.HALF_UP);
        return base.multiply(dailyFraction).multiply(BigDecimal.valueOf(dpd));
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(0, RoundingMode.HALF_UP);
    }

    private String borrowerWalletOwnerType(LoanRequest loan) {
        if (loan.getProductId() == null || loan.getProductId().isBlank()) {
            return "PERSONAL";
        }
        return loanProductService.findProductById(loan.getProductId())
                .map(LoanProduct::getCategory)
                .filter(category -> category == ProductCategory.BUSINESS || category == ProductCategory.ENTERPRISE)
                .map(category -> "BUSINESS")
                .orElse("PERSONAL");
    }

    private RepaymentScheduleResponse toResponse(RepaymentSchedule s) {
        int displayDpd = 0;
        LocalDate today = LocalDate.now(TZ);
        if (s.getStatus() != RepaymentStatus.PAID && s.getDueDate() != null && s.getDueDate().isBefore(today)) {
            displayDpd = Math.toIntExact(ChronoUnit.DAYS.between(s.getDueDate(), today));
        }
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
                .interestPenalty(s.getInterestPenalty())
                .principalPenalty(s.getPrincipalPenalty())
                .principalOutstanding(s.getPrincipalOutstanding())
                .interestOutstanding(s.getInterestOutstanding())
                .status(s.getStatus())
                .dpd(displayDpd)
                .paidAt(s.getPaidAt())
                .build();
    }

    /** Danh sách kỳ trả nợ đến hạn theo ngày chỉ định (mặc định hôm nay) — CMS theo dõi ai đã/chưa trả. */
    @Transactional(readOnly = true)
    public List<com.p2plending.loan.dto.response.DueTodayItem> getDueTodayList(LocalDate date) {
        LocalDate today = date != null ? date : LocalDate.now(TZ);
        // Trả về tất cả kỳ chưa trả (PENDING/OVERDUE/PARTIAL) có dueDate ≤ today
        // để admin thấy đủ kỳ cũ còn nợ chứ không chỉ kỳ đúng hôm nay
        List<RepaymentSchedule> schedules =
                scheduleRepository.findByDueDateLessThanEqualAndStatusNotAndIsDeletedFalseOrderByLoanIdAscDueDateAscPeriodNumberAsc(
                        today, RepaymentStatus.PAID);

        java.util.Set<String> loanIds = schedules.stream()
                .map(RepaymentSchedule::getLoanId)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, LoanRequest> loans = new HashMap<>();
        loanRequestRepository.findAllById(loanIds)
                .forEach(l -> loans.put(l.getId(), l));

        // Tính tổng dư nợ (gốc+lãi+phí phạt) của tất cả kỳ chưa trả theo từng khoản
        Map<String, BigDecimal> totalDebtByLoan = schedules.stream().collect(
                java.util.stream.Collectors.groupingBy(
                        RepaymentSchedule::getLoanId,
                        java.util.stream.Collectors.reducing(BigDecimal.ZERO,
                                RepaymentSchedule::getTotalOutstanding, BigDecimal::add)));

        return schedules.stream().map(s -> {
            LoanRequest loan = loans.get(s.getLoanId());
            return com.p2plending.loan.dto.response.DueTodayItem.builder()
                    .scheduleId(s.getId())
                    .loanId(s.getLoanId())
                    .loanCode(loan != null ? loan.getLoanCode() : null)
                    .borrowerId(loan != null ? loan.getBorrowerId() : null)
                    .periodNumber(s.getPeriodNumber())
                    .dueDate(s.getDueDate())
                    .principalDue(money(s.getPrincipalDue()))
                    .interestDue(money(s.getInterestDue()))
                    .totalDue(money(s.getTotalDue()))
                    .lateFee(money(s.getLateFee()))
                    .paidAmount(money(s.getPaidAmount()))
                    .lateFeePaid(money(s.getLateFeePaid()))
                    .remaining(money(s.getTotalOutstanding()))
                    .totalDebt(money(totalDebtByLoan.getOrDefault(s.getLoanId(), BigDecimal.ZERO)))
                    .status(s.getStatus().name())
                    .dpd(s.getDpd())
                    .build();
        }).collect(java.util.stream.Collectors.toList());
    }
}
