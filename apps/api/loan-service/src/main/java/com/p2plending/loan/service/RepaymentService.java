package com.p2plending.loan.service;

import com.p2plending.loan.config.CacheConfig;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.entity.RepaymentSchedule;
import com.p2plending.loan.domain.entity.RepaymentTransaction;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.PaymentChannel;
import com.p2plending.loan.domain.enums.RepaymentMethod;
import com.p2plending.loan.domain.enums.RepaymentStatus;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.domain.repository.RepaymentScheduleRepository;
import com.p2plending.loan.domain.repository.RepaymentTransactionRepository;
import com.p2plending.loan.dto.request.RecordPaymentRequest;
import com.p2plending.loan.dto.response.RepaymentScheduleResponse;
import com.p2plending.loan.exception.InvalidLoanStateException;
import com.p2plending.loan.exception.LoanNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepaymentService {

    private static final ZoneId TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Trạng thái khoản chấp nhận ghi nhận trả nợ. */
    private static final Set<LoanStatus> PAYABLE_STATUSES =
            EnumSet.of(LoanStatus.FUNDED, LoanStatus.REPAYING, LoanStatus.DEFAULTED);

    /** Khoản đang trong vòng đời trả nợ — đối tượng quét DPD. */
    private static final Set<LoanStatus> SERVICING_STATUSES =
            EnumSet.of(LoanStatus.FUNDED, LoanStatus.REPAYING);

    private final RepaymentScheduleRepository    scheduleRepository;
    private final RepaymentTransactionRepository transactionRepository;
    private final LoanRequestRepository          loanRequestRepository;
    private final LoanProductService             loanProductService;
    private final RepaymentScheduleGenerator     generator;
    private final CacheManager                   cacheManager;

    /** Ngưỡng DPD đánh DEFAULTED — VNFITE dùng 30 ngày (siết chặt hơn chuẩn NHNN 90 ngày). */
    @Value("${app.dpd.default-threshold:30}")
    private int defaultDpdThreshold;

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

        transactionRepository.save(RepaymentTransaction.builder()
                .loanId(loanId)
                .scheduleId(firstTouchedScheduleId)
                .amount(request.getAmount())
                .paidAt(paidAt)
                .channel(request.getChannel() != null ? request.getChannel() : PaymentChannel.MANUAL_ADMIN)
                .externalRef(request.getExternalRef())
                .recordedBy(request.getRecordedBy())
                .note(request.getNote())
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

            for (RepaymentSchedule s : schedules) {
                if (s.isSettled()) continue;
                if (s.getDueDate().isBefore(today)) {
                    int dpd = (int) ChronoUnit.DAYS.between(s.getDueDate(), today);
                    s.setDpd(dpd);
                    s.setStatus(RepaymentStatus.OVERDUE);
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

    // ── Helpers ───────────────────────────────────────────────────

    private void evictLoanCaches(String loanId) {
        Cache byId = cacheManager.getCache(CacheConfig.CACHE_LOAN_BY_ID);
        if (byId != null) byId.evict(loanId);
        Cache list = cacheManager.getCache(CacheConfig.CACHE_LOANS);
        if (list != null) list.clear();
    }

    private RepaymentScheduleResponse toResponse(RepaymentSchedule s) {
        return RepaymentScheduleResponse.builder()
                .periodNumber(s.getPeriodNumber())
                .dueDate(s.getDueDate())
                .principalDue(s.getPrincipalDue())
                .interestDue(s.getInterestDue())
                .totalDue(s.getTotalDue())
                .paidAmount(s.getPaidAmount())
                .status(s.getStatus())
                .dpd(s.getDpd())
                .paidAt(s.getPaidAt())
                .build();
    }
}
