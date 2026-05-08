package com.p2plending.cms.service;

import com.p2plending.cms.domain.entity.CmsUser;
import com.p2plending.cms.domain.enums.UserAccountStatus;
import com.p2plending.cms.domain.repository.CmsUserRepository;
import com.p2plending.cms.dto.request.KycDecisionRequest;
import com.p2plending.cms.dto.request.UserStatusRequest;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.dto.response.UserSummaryResponse;
import com.p2plending.cms.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final CmsUserRepository userRepo;

    @Transactional(readOnly = true)
    public PagedResponse<UserSummaryResponse> getUsers(
            String kycStatus, String role, UserAccountStatus status,
            String search, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PagedResponse.from(
                userRepo.findWithFilters(kycStatus, role, status, search, pageable)
                        .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse getUser(Long userId) {
        return toResponse(findOrThrow(userId));
    }

    @Transactional
    public UserSummaryResponse decideKyc(Long userId, KycDecisionRequest req) {
        CmsUser user = findOrThrow(userId);
        user.setKycStatus(req.getDecision().name());
        return toResponse(userRepo.save(user));
    }

    @Transactional
    public UserSummaryResponse updateStatus(Long userId, UserStatusRequest req) {
        CmsUser user = findOrThrow(userId);
        user.setAccountStatus(req.getStatus());
        return toResponse(userRepo.save(user));
    }

    private CmsUser findOrThrow(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private UserSummaryResponse toResponse(CmsUser u) {
        return UserSummaryResponse.builder()
                .userId(u.getUserId()).email(u.getEmail()).fullName(u.getFullName())
                .phone(u.getPhone()).role(u.getRole()).kycStatus(u.getKycStatus())
                .accountStatus(u.getAccountStatus()).createdAt(u.getCreatedAt())
                .build();
    }
}
