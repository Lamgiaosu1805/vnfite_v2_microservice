package com.p2plending.cms.service;

import com.p2plending.cms.domain.enums.UserAccountStatus;
import com.p2plending.cms.dto.request.KycDecisionRequest;
import com.p2plending.cms.dto.request.UserStatusRequest;
import com.p2plending.cms.dto.response.CustomerDetailResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.dto.response.UserSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final SourceServiceClient sourceServiceClient;

    public PagedResponse<UserSummaryResponse> getUsers(
            String kycStatus, String role, UserAccountStatus status,
            String search, int page, int size) {
        return sourceServiceClient.getUsers(kycStatus, role, status, search, page, size);
    }

    public UserSummaryResponse getUser(String userId) {
        return sourceServiceClient.getUser(userId);
    }

    public CustomerDetailResponse getCustomerDetail(String userId, int transactionPage, int transactionSize,
                                                    int loanPage, int loanSize) {
        return sourceServiceClient.getCustomerDetail(userId, transactionPage, transactionSize, loanPage, loanSize);
    }

    public UserSummaryResponse decideKyc(String userId, KycDecisionRequest req) {
        throw new UnsupportedOperationException("CMS no longer stores user mirror data. Send KYC decisions to auth-service.");
    }

    public UserSummaryResponse updateStatus(String userId, UserStatusRequest req) {
        throw new UnsupportedOperationException("CMS no longer stores user mirror data. Send account-status changes to auth-service.");
    }
}
