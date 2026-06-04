package com.p2plending.cms.service;

import com.p2plending.cms.domain.enums.UserAccountStatus;
import com.p2plending.cms.dto.request.KycDecisionRequest;
import com.p2plending.cms.dto.request.UserStatusRequest;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.dto.response.UserSummaryResponse;
import com.p2plending.cms.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserManagementService {

    public PagedResponse<UserSummaryResponse> getUsers(
            String kycStatus, String role, UserAccountStatus status,
            String search, int page, int size) {
        return PagedResponse.empty(page, size);
    }

    public UserSummaryResponse getUser(String userId) {
        throw new ResourceNotFoundException("User read-model is disabled. Query auth-service for user details: " + userId);
    }

    public UserSummaryResponse decideKyc(String userId, KycDecisionRequest req) {
        throw new UnsupportedOperationException("CMS no longer stores user mirror data. Send KYC decisions to auth-service.");
    }

    public UserSummaryResponse updateStatus(String userId, UserStatusRequest req) {
        throw new UnsupportedOperationException("CMS no longer stores user mirror data. Send account-status changes to auth-service.");
    }
}
