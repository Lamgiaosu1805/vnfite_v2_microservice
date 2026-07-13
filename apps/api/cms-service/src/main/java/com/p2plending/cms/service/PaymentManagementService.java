package com.p2plending.cms.service;

import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.dto.response.SystemTransactionSummaryResponse;
import com.p2plending.cms.security.CmsPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PaymentManagementService {

    private final SourceServiceClient sourceServiceClient;

    public PagedResponse<SystemTransactionSummaryResponse> getTransactions(
            String type,
            String status,
            LocalDate from,
            LocalDate to,
            String search,
            int page,
            int size) {
        return sourceServiceClient.getSystemMoneyTransactions(
                type, status, from, to, search, page, size);
    }

    public JsonNode getManualDeposits(String status, int page, int size) {
        return sourceServiceClient.getManualDeposits(status, page, size);
    }

    public JsonNode approveManualDeposit(String requestId, CmsPrincipal operator) {
        return sourceServiceClient.approveManualDeposit(requestId,
                operator != null ? operator.displayName() : "CMS");
    }

    public JsonNode rejectManualDeposit(String requestId, String reason, CmsPrincipal operator) {
        return sourceServiceClient.rejectManualDeposit(requestId,
                operator != null ? operator.displayName() : "CMS", reason);
    }
}
