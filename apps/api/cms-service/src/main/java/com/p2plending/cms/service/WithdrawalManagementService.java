package com.p2plending.cms.service;

import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.dto.response.WithdrawalSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WithdrawalManagementService {

    private final SourceServiceClient sourceServiceClient;

    /** Danh sách withdrawal đang xử lý / thất bại để ops giám sát. */
    public PagedResponse<WithdrawalSummaryResponse> getForMonitoring(List<String> statuses, int page, int size) {
        return sourceServiceClient.getWithdrawalsForMonitoring(statuses, page, size);
    }

    /** Ops retry chuyển tiền thủ công khi giao dịch ở TRANSFER_FAILED. */
    public void retry(String adminId, String withdrawalId) {
        sourceServiceClient.retryWithdrawal(adminId, withdrawalId);
    }

    /** Ops resolve giao dịch kẹt ở PROCESSING sau khi đã xác minh tại TIKLUY/MB. */
    public void resolve(String adminId, String withdrawalId, boolean wasSent, String ftNumber, String note) {
        sourceServiceClient.resolveWithdrawal(adminId, withdrawalId, wasSent, ftNumber, note);
    }
}
