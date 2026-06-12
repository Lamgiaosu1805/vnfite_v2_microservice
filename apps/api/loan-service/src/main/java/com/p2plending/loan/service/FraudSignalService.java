package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.repository.LoanRequestRepository;
import com.p2plending.loan.dto.response.AppraisalSuggestionResponse.FraudCheck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Phát hiện gian lận bằng đối chiếu dữ liệu nội bộ {@code loan_requests} (không cần tích hợp ngoài):
 * <ul>
 *   <li><b>Velocity</b>: người gọi vốn có nhiều khoản đang mở cùng lúc (vay chồng).</li>
 *   <li><b>Trùng người tham chiếu</b>: một SĐT tham chiếu đứng tên cho nhiều người gọi vốn khác nhau
 *       (dấu hiệu môi giới / vòng tham chiếu).</li>
 *   <li><b>Hai người tham chiếu khai cùng một SĐT</b>.</li>
 * </ul>
 * Tất cả chỉ TƯ VẤN — quyết định cuối thuộc thẩm định viên/ban lãnh đạo. Ngưỡng để ở hằng số,
 * chỉnh sau khi có dữ liệu thực tế nếu cần.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudSignalService {

    /** Khoản đang "mở" (chưa kết thúc) — dùng cho velocity. */
    private static final List<LoanStatus> OPEN_STATUSES = List.of(
            LoanStatus.PENDING_REVIEW, LoanStatus.PENDING_APPROVAL, LoanStatus.AWAITING_BORROWER_APPROVAL,
            LoanStatus.ACTIVE, LoanStatus.FUNDED, LoanStatus.AWAITING_DISBURSEMENT,
            LoanStatus.DISBURSED, LoanStatus.REPAYING);

    /** Số người gọi vốn KHÁC dùng chung 1 SĐT tham chiếu để coi là rủi ro cao. */
    private static final long SHARED_REFERENCE_HIGH = 3;

    private final LoanRequestRepository loanRequestRepository;

    @Transactional(readOnly = true)
    public List<FraudCheck> detect(LoanRequest loan) {
        List<FraudCheck> checks = new ArrayList<>();

        // 1) Velocity — khoản đang mở khác của cùng người gọi vốn
        long openOthers = loanRequestRepository.countOpenLoansByBorrowerExcluding(
                loan.getBorrowerId(), loan.getId(), OPEN_STATUSES);
        if (openOthers >= 2) {
            checks.add(fraud("VELOCITY_OPEN_LOANS", "HIGH", "Vay chồng nhiều khoản",
                    "Người gọi vốn đang có " + openOthers + " khoản khác chưa tất toán/đang xử lý — rủi ro vay chồng cao."));
        } else if (openOthers == 1) {
            checks.add(fraud("VELOCITY_OPEN_LOANS", "MEDIUM", "Đang có khoản khác",
                    "Người gọi vốn đang có 1 khoản khác chưa tất toán/đang xử lý — kiểm tra khả năng trả nợ tổng."));
        }

        // 2) Trùng người tham chiếu giữa nhiều người gọi vốn khác nhau
        Set<String> refPhones = new LinkedHashSet<>();
        if (StringUtils.hasText(loan.getRef1Phone())) refPhones.add(loan.getRef1Phone().trim());
        if (StringUtils.hasText(loan.getRef2Phone())) refPhones.add(loan.getRef2Phone().trim());
        for (String phone : refPhones) {
            long others = loanRequestRepository.findDistinctBorrowersUsingReferencePhone(phone).stream()
                    .filter(b -> b != null && !b.equals(loan.getBorrowerId()))
                    .distinct().count();
            if (others >= SHARED_REFERENCE_HIGH) {
                checks.add(fraud("SHARED_REFERENCE", "HIGH", "Người tham chiếu dùng lại nhiều nơi",
                        "SĐT tham chiếu " + phone + " đang đứng tên cho " + others
                                + " người gọi vốn khác — dấu hiệu môi giới/vòng tham chiếu."));
            } else if (others == 2) {
                checks.add(fraud("SHARED_REFERENCE", "MEDIUM", "Người tham chiếu trùng",
                        "SĐT tham chiếu " + phone + " còn xuất hiện ở hồ sơ của " + others
                                + " người gọi vốn khác — nên xác minh."));
            }
        }

        // 3) Hai người tham chiếu khai cùng một SĐT
        if (StringUtils.hasText(loan.getRef1Phone()) && StringUtils.hasText(loan.getRef2Phone())
                && loan.getRef1Phone().trim().equals(loan.getRef2Phone().trim())) {
            checks.add(fraud("SAME_REF_PHONE", "MEDIUM", "Hai người tham chiếu cùng SĐT",
                    "Người tham chiếu 1 và 2 khai cùng một số điện thoại — cần xác minh là hai người độc lập."));
        }

        if (!checks.isEmpty()) {
            log.info("Fraud signals loan {}: {}", loan.getId(),
                    checks.stream().map(c -> c.getCode() + "/" + c.getSeverity()).toList());
        }
        return checks;
    }

    private FraudCheck fraud(String code, String severity, String title, String detail) {
        return FraudCheck.builder().code(code).severity(severity).title(title).detail(detail).build();
    }
}
