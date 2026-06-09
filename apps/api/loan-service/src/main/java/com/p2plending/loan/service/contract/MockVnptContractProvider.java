package com.p2plending.loan.service.contract;

import com.p2plending.loan.domain.entity.LoanContract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Mock VNPT eContract — không gọi mạng, chỉ sinh mã + URL giả lập.
 *
 * <p>Mô phỏng theo cách {@code ImageStorageService} mock của auth-service: trả về định danh
 * có tiền tố để dễ nhận biết đây là dữ liệu mock. Khi có VNPT thật, thay bằng implementation
 * gọi API VNPT và đánh dấu bean này {@code @Profile("!prod")} hoặc tắt qua cấu hình.
 */
@Service
@Slf4j
public class MockVnptContractProvider implements ContractSignatureProvider {

    private static final ZoneId TZ = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String DOC_BASE = "https://econtract.mock.vnfite/contracts/";

    @Override
    public IssueResult issue(LoanContract draft) {
        String providerContractId = "mockvnpt_" + UUID.randomUUID().toString().replace("-", "");
        String documentUrl = DOC_BASE + providerContractId + ".pdf";
        log.info("[MockVNPT] Issued contract loanId={} type={} party={} → providerId={}",
                draft.getLoanId(), draft.getContractType(), draft.getPartyId(), providerContractId);
        return new IssueResult(providerContractId, documentUrl);
    }

    @Override
    public SignResult sign(LoanContract contract) {
        LocalDateTime signedAt = LocalDateTime.now(TZ);
        String signedUrl = DOC_BASE + contract.getProviderContractId() + "-signed.pdf";
        log.info("[MockVNPT] Signed contract id={} providerId={} at={}",
                contract.getId(), contract.getProviderContractId(), signedAt);
        return new SignResult(signedUrl, signedAt);
    }
}
