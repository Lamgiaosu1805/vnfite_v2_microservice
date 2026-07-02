package com.p2plending.cms.service;

import com.p2plending.cms.domain.enums.UserAccountStatus;
import com.p2plending.cms.dto.request.KycDecisionRequest;
import com.p2plending.cms.dto.request.UserStatusRequest;
import com.p2plending.cms.dto.response.CustomerDetailResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.dto.response.ResetCustomerPasswordResponse;
import com.p2plending.cms.dto.response.UserSummaryResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final SourceServiceClient sourceServiceClient;

    public PagedResponse<UserSummaryResponse> getUsers(
            String kycStatus, Boolean blacklisted, String role, UserAccountStatus status,
            String search, int page, int size) {
        return sourceServiceClient.getUsers(kycStatus, blacklisted, role, status, search, page, size);
    }

    public UserSummaryResponse getUser(String userId) {
        return sourceServiceClient.getUser(userId);
    }

    public CustomerDetailResponse getCustomerDetail(String userId, int transactionPage, int transactionSize,
                                                    int loanPage, int loanSize,
                                                    int investmentPage, int investmentSize,
                                                    String investmentStatus) {
        return sourceServiceClient.getCustomerDetail(userId, transactionPage, transactionSize, loanPage, loanSize,
                investmentPage, investmentSize, investmentStatus);
    }

    public UserSummaryResponse decideKyc(String userId, KycDecisionRequest req) {
        throw new UnsupportedOperationException("CMS no longer stores user mirror data. Send KYC decisions to auth-service.");
    }

    // ─── Hồ sơ doanh nghiệp — passthrough auth-service / credit-service ─────

    public JsonNode getBusinessProfiles(String status, int page, int size) {
        return sourceServiceClient.getBusinessProfiles(status, page, size);
    }

    public JsonNode getBusinessProfile(String userId) {
        return sourceServiceClient.getBusinessProfile(userId);
    }

    public void decideBusinessProfile(String userId, boolean approved, String reason, String reviewedBy) {
        sourceServiceClient.decideBusinessProfile(userId, approved, reason, reviewedBy);
    }

    public JsonNode analyzeBusinessLicense(String userId) {
        return sourceServiceClient.analyzeBusinessLicense(userId);
    }

    public UserSummaryResponse updateStatus(String userId, UserStatusRequest req) {
        throw new UnsupportedOperationException("CMS no longer stores user mirror data. Send account-status changes to auth-service.");
    }

    public ResetCustomerPasswordResponse resetPassword(String userId) {
        return sourceServiceClient.resetCustomerPassword(userId);
    }

    public void resetDevice(String userId) {
        sourceServiceClient.resetCustomerDevice(userId);
    }

    public UserSummaryResponse setBlacklist(String userId, boolean blacklisted, String reason) {
        return sourceServiceClient.setCustomerBlacklist(userId, blacklisted, reason);
    }
}
