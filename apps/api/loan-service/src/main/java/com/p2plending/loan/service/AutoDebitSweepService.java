package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.RepaymentAutoDebitAudit;
import com.p2plending.loan.domain.enums.AutoDebitLoanResultStatus;
import com.p2plending.loan.domain.repository.RepaymentAutoDebitAuditRepository;
import com.p2plending.loan.dto.response.AutoDebitLoanResult;
import com.p2plending.loan.dto.response.AutoDebitSweepResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoDebitSweepService {

    private static final ZoneId TZ = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RepaymentService repaymentService;
    private final RepaymentAutoDebitAuditRepository auditRepository;

    public AutoDebitSweepResponse runSweep(String triggerSource, String triggeredBy) {
        LocalDateTime startedAt = LocalDateTime.now(TZ);
        List<String> loanIds = repaymentService.findAutoDebitLoanIds();

        int dueLoans = 0;
        int settledFull = 0;
        int settledPartial = 0;
        int noBalance = 0;
        int balanceError = 0;
        int noDue = 0;
        int failed = 0;
        BigDecimal amountCollected = BigDecimal.ZERO;
        List<String> errors = new ArrayList<>();

        log.info("Auto-debit sweep triggered: source={} by={} loans={}",
                triggerSource, triggeredBy, loanIds.size());

        for (String loanId : loanIds) {
            try {
                AutoDebitLoanResult result = repaymentService.autoDebitLoan(loanId);
                if (result.getStatus() != AutoDebitLoanResultStatus.NO_DUE) {
                    dueLoans++;
                }
                switch (result.getStatus()) {
                    case SETTLED_FULL -> {
                        settledFull++;
                        amountCollected = amountCollected.add(result.getAmountCollected());
                    }
                    case SETTLED_PARTIAL -> {
                        settledPartial++;
                        amountCollected = amountCollected.add(result.getAmountCollected());
                    }
                    case NO_BALANCE -> noBalance++;
                    case BALANCE_ERROR -> balanceError++;
                    case NO_DUE -> noDue++;
                }
            } catch (Exception e) {
                failed++;
                if (errors.size() < 5) {
                    errors.add(loanId + ": " + (e.getMessage() != null ? e.getMessage() : "unknown"));
                }
                log.error("Auto-debit loan {} thất bại: {}", loanId, e.getMessage(), e);
            }
        }

        LocalDateTime finishedAt = LocalDateTime.now(TZ);
        String errorSummary = errors.isEmpty() ? null : String.join(" | ", errors);
        if (errorSummary != null && errorSummary.length() > 1000) {
            errorSummary = errorSummary.substring(0, 1000);
        }

        RepaymentAutoDebitAudit audit = auditRepository.save(RepaymentAutoDebitAudit.builder()
                .triggerSource(triggerSource)
                .triggeredBy(triggeredBy)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .scannedLoans(loanIds.size())
                .dueLoans(dueLoans)
                .settledFull(settledFull)
                .settledPartial(settledPartial)
                .noBalance(noBalance)
                .balanceError(balanceError)
                .noDue(noDue)
                .failed(failed)
                .amountCollected(amountCollected)
                .errorSummary(errorSummary)
                .build());

        log.info("Auto-debit sweep done: audit={} scanned={} due={} full={} partial={} noBalance={} balanceError={} failed={} amount={}",
                audit.getId(), loanIds.size(), dueLoans, settledFull, settledPartial,
                noBalance, balanceError, failed, amountCollected);

        return AutoDebitSweepResponse.builder()
                .auditId(audit.getId())
                .triggerSource(audit.getTriggerSource())
                .triggeredBy(audit.getTriggeredBy())
                .startedAt(audit.getStartedAt())
                .finishedAt(audit.getFinishedAt())
                .scannedLoans(audit.getScannedLoans())
                .dueLoans(audit.getDueLoans())
                .settledFull(audit.getSettledFull())
                .settledPartial(audit.getSettledPartial())
                .noBalance(audit.getNoBalance())
                .balanceError(audit.getBalanceError())
                .noDue(audit.getNoDue())
                .failed(audit.getFailed())
                .amountCollected(audit.getAmountCollected())
                .errorSummary(audit.getErrorSummary())
                .build();
    }
}
