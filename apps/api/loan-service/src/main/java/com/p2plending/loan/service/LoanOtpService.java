package com.p2plending.loan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.loan.config.RedisNamespaceProperties;
import com.p2plending.loan.dto.request.LoanCreateRequest;
import com.p2plending.loan.dto.request.PendingLoanData;
import com.p2plending.loan.dto.response.LoanOtpInitResponse;
import com.p2plending.loan.dto.response.LoanResponse;
import com.p2plending.loan.exception.InvalidLoanStateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Xử lý OTP xác nhận khi tạo khoản gọi vốn.
 *
 * Flow:
 *   1. init()    — validate sơ bộ, lưu pending data + OTP vào Redis (TTL 10 phút)
 *   2. confirm() — verify OTP, xóa Redis key, gọi LoanService.createLoan()
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanOtpService {

    private static final String KEY_PREFIX = "pending_loan:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String MOCK_OTP = "000000";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LoanService loanService;
    private final RedisNamespaceProperties redisNamespaceProperties;

    @Value("${app.otp.mock:true}")
    private boolean mockOtp;

    // ── Init ─────────────────────────────────────────────────────────────────

    public LoanOtpInitResponse init(LoanCreateRequest request, String borrowerId) {
        String otp = mockOtp ? MOCK_OTP : generateOtp();

        PendingLoanData pending = PendingLoanData.builder()
                .borrowerId(borrowerId)
                .otp(otp)
                .productCode(request.getProductCode())
                .amount(request.getAmount())
                .termMonths(request.getTermMonths())
                .repaymentDay(request.getRepaymentDay())
                .purpose(request.getPurpose())
                .documents(request.getDocuments())
                .monthlyIncome(request.getMonthlyIncome())
                .occupation(request.getOccupation())
                .workplace(request.getWorkplace())
                .currentAddress(request.getCurrentAddress())
                .commune(request.getCommune())
                .province(request.getProvince())
                .ref1FullName(request.getRef1FullName())
                .ref1Relationship(request.getRef1Relationship())
                .ref1Phone(request.getRef1Phone())
                .ref1Address(request.getRef1Address())
                .ref2FullName(request.getRef2FullName())
                .ref2Relationship(request.getRef2Relationship())
                .ref2Phone(request.getRef2Phone())
                .ref2Address(request.getRef2Address())
                .build();

        try {
            String json = objectMapper.writeValueAsString(pending);
            redisTemplate.opsForValue().set(pendingKey(borrowerId), json, TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize pending loan data for borrower {}", borrowerId, e);
            throw new InvalidLoanStateException("Không thể lưu thông tin tạm thời. Vui lòng thử lại.");
        }

        // TODO: production — gửi OTP qua Kafka → notification-service
        if (!mockOtp) {
            log.info("[LoanOtp] OTP generated for borrower {} (send via notification-service)", borrowerId);
        }

        LoanOtpInitResponse.LoanOtpInitResponseBuilder builder = LoanOtpInitResponse.builder()
                .message("Mã OTP đã được gửi đến số điện thoại của bạn. Có hiệu lực trong 10 phút.");

        if (mockOtp) {
            builder.otp(otp);
        }

        return builder.build();
    }

    // ── Confirm ───────────────────────────────────────────────────────────────

    public LoanResponse confirm(String otp, String borrowerId) {
        String key = pendingKey(borrowerId);
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phiên xác nhận đã hết hạn hoặc không tồn tại. Vui lòng thực hiện lại từ đầu.");
        }

        PendingLoanData pending;
        try {
            pending = objectMapper.readValue(json, PendingLoanData.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize pending loan data for borrower {}", borrowerId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Lỗi xử lý dữ liệu. Vui lòng thử lại.");
        }

        if (!pending.getOtp().equals(otp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mã OTP không đúng. Vui lòng kiểm tra lại.");
        }

        // Consume once — xóa key khỏi Redis
        redisTemplate.delete(key);

        // Rebuild LoanCreateRequest từ pending data
        LoanCreateRequest createRequest = buildCreateRequest(pending);
        return loanService.createLoan(createRequest, borrowerId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LoanCreateRequest buildCreateRequest(PendingLoanData p) {
        LoanCreateRequest r = new LoanCreateRequest();
        r.setProductCode(p.getProductCode());
        r.setAmount(p.getAmount());
        r.setTermMonths(p.getTermMonths());
        r.setRepaymentDay(p.getRepaymentDay());
        r.setPurpose(p.getPurpose());
        r.setDocuments(p.getDocuments());
        r.setMonthlyIncome(p.getMonthlyIncome());
        r.setOccupation(p.getOccupation());
        r.setWorkplace(p.getWorkplace());
        r.setCurrentAddress(p.getCurrentAddress());
        r.setCommune(p.getCommune());
        r.setProvince(p.getProvince());
        r.setRef1FullName(p.getRef1FullName());
        r.setRef1Relationship(p.getRef1Relationship());
        r.setRef1Phone(p.getRef1Phone());
        r.setRef1Address(p.getRef1Address());
        r.setRef2FullName(p.getRef2FullName());
        r.setRef2Relationship(p.getRef2Relationship());
        r.setRef2Phone(p.getRef2Phone());
        r.setRef2Address(p.getRef2Address());
        return r;
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String pendingKey(String borrowerId) {
        return redisNamespaceProperties.qualify(KEY_PREFIX + borrowerId);
    }
}
