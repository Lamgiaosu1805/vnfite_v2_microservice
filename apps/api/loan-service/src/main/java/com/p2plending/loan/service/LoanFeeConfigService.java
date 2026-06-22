package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.LoanFeeConfig;
import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.repository.LoanFeeConfigRepository;
import com.p2plending.loan.dto.request.FeeConfigUpdateRequest;
import com.p2plending.loan.dto.response.LoanFeeConfigResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanFeeConfigService {

    private static final String APPRAISAL = "APPRAISAL";

    private final LoanFeeConfigRepository feeConfigRepository;

    @Transactional(readOnly = true)
    public List<LoanFeeConfigResponse> getAll() {
        return feeConfigRepository.findAll().stream()
                .map(LoanFeeConfigResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LoanFeeConfigResponse getByType(String feeType) {
        return feeConfigRepository.findByFeeType(feeType)
                .map(LoanFeeConfigResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình phí: " + feeType));
    }

    @Transactional
    public LoanFeeConfigResponse upsert(FeeConfigUpdateRequest req, String updatedBy) {
        LoanFeeConfig cfg = feeConfigRepository.findByFeeType(req.getFeeType())
                .orElseGet(() -> LoanFeeConfig.builder()
                        .feeType(req.getFeeType())
                        .feeName(req.getFeeType())
                        .feeAmount(BigDecimal.ZERO)
                        .calcType(LoanFeeConfig.CalcType.FIXED)
                        .vatRate(new BigDecimal("0.1000"))
                        .isActive(true)
                        .build());

        if (req.getFeeName()   != null) cfg.setFeeName(req.getFeeName());
        if (req.getFeeAmount() != null) cfg.setFeeAmount(req.getFeeAmount());
        if (req.getCalcType()  != null) cfg.setCalcType(req.getCalcType());
        if (req.getVatRate()   != null) cfg.setVatRate(req.getVatRate());
        if (req.getIsActive()  != null) cfg.setActive(req.getIsActive());
        cfg.setUpdatedBy(updatedBy);

        log.info("FeeConfig [{}] updated by {}: amount={}, calcType={}, vatRate={}",
                cfg.getFeeType(), updatedBy, cfg.getFeeAmount(), cfg.getCalcType(), cfg.getVatRate());
        return LoanFeeConfigResponse.from(feeConfigRepository.save(cfg));
    }

    public record FeeEstimate(BigDecimal appraisalFee, BigDecimal vatAmount,
                              BigDecimal totalFee, BigDecimal netDisbursement) {}

    /**
     * Ước tính phí cho một khoản vay (chỉ đọc, không ghi vào DB).
     * Dùng để hiển thị cho người gọi vốn trước khi xác nhận điều khoản.
     */
    @Transactional(readOnly = true)
    public FeeEstimate estimateFees(BigDecimal loanAmount) {
        List<LoanFeeConfig> activeFees = feeConfigRepository.findAllByIsActiveTrue();

        BigDecimal appraisalFee = BigDecimal.ZERO;
        BigDecimal totalFee     = BigDecimal.ZERO;

        for (LoanFeeConfig cfg : activeFees) {
            BigDecimal fee = cfg.calculateFee(loanAmount).setScale(0, RoundingMode.HALF_UP);
            if (APPRAISAL.equals(cfg.getFeeType())) appraisalFee = fee;
            totalFee = totalFee.add(fee);
        }

        BigDecimal vatAmount = BigDecimal.ZERO;
        for (LoanFeeConfig cfg : activeFees) {
            BigDecimal fee = cfg.calculateFee(loanAmount).setScale(0, RoundingMode.HALF_UP);
            vatAmount = vatAmount.add(cfg.calculateVat(fee));
        }
        vatAmount = vatAmount.setScale(0, RoundingMode.HALF_UP);

        BigDecimal grandTotal  = totalFee.add(vatAmount);
        BigDecimal netAmount   = loanAmount.subtract(grandTotal).max(BigDecimal.ZERO);

        return new FeeEstimate(appraisalFee, vatAmount, grandTotal, netAmount);
    }

    /**
     * Tính và ghi phí vào loan. Gọi ngay trước khi credit tiền cho người gọi vốn.
     * Trả về số tiền thực nhận sau khi trừ phí.
     */
    @Transactional
    public BigDecimal applyFeesToLoan(LoanRequest loan, BigDecimal disbursementAmount) {
        List<LoanFeeConfig> activeFees = feeConfigRepository.findAllByIsActiveTrue();

        BigDecimal totalFee = BigDecimal.ZERO;
        BigDecimal appraisalFee = BigDecimal.ZERO;

        for (LoanFeeConfig cfg : activeFees) {
            BigDecimal fee = cfg.calculateFee(disbursementAmount).setScale(0, RoundingMode.HALF_UP);
            if (APPRAISAL.equals(cfg.getFeeType())) {
                appraisalFee = fee;
            }
            totalFee = totalFee.add(fee);
        }

        BigDecimal vatAmount = BigDecimal.ZERO;
        for (LoanFeeConfig cfg : activeFees) {
            BigDecimal fee = cfg.calculateFee(disbursementAmount).setScale(0, RoundingMode.HALF_UP);
            vatAmount = vatAmount.add(cfg.calculateVat(fee));
        }
        vatAmount = vatAmount.setScale(0, RoundingMode.HALF_UP);

        BigDecimal grandTotal = totalFee.add(vatAmount);
        BigDecimal netAmount  = disbursementAmount.subtract(grandTotal);
        if (netAmount.compareTo(BigDecimal.ZERO) < 0) netAmount = BigDecimal.ZERO;

        loan.setAppraisalFee(appraisalFee);
        loan.setVatAmount(vatAmount);
        loan.setTotalFee(grandTotal);
        loan.setNetDisbursement(netAmount);

        log.info("Loan {} — appraisalFee={}, VAT={}, totalFee={}, netDisbursement={}",
                loan.getLoanCode(), appraisalFee, vatAmount, grandTotal, netAmount);
        return netAmount;
    }
}
