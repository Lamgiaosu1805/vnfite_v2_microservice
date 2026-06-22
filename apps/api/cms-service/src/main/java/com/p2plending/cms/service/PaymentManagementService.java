package com.p2plending.cms.service;

import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.dto.response.SystemTransactionSummaryResponse;
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
}
