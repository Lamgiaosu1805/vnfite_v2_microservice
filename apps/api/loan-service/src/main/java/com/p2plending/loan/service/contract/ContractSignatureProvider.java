package com.p2plending.loan.service.contract;

import com.p2plending.loan.domain.entity.LoanContract;

import java.time.LocalDateTime;

/**
 * Cổng ký số hợp đồng điện tử.
 *
 * <p>Hiện có một implementation duy nhất {@link MockVnptContractProvider} (mock).
 * Khi tích hợp VNPT eContract thật (xem {@code /Users/lamgs/Desktop/API/VNFITE_ECONTRACT}),
 * chỉ cần thêm một implementation mới gọi API VNPT và bật theo profile/cấu hình —
 * không phải đụng tới {@code ContractService}.
 */
public interface ContractSignatureProvider {

    /** Phát hành hợp đồng tại nhà cung cấp → trả về mã hợp đồng + URL bản chưa ký. */
    IssueResult issue(LoanContract draft);

    /** Ký hợp đồng (sau khi đã xác thực OTP ở tầng service) → trả về URL bản đã ký + thời điểm ký. */
    SignResult sign(LoanContract contract);

    record IssueResult(String providerContractId, String documentUrl) {}

    record SignResult(String signedDocumentUrl, LocalDateTime signedAt) {}
}
