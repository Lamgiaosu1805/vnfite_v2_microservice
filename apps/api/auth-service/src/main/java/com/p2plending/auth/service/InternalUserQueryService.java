package com.p2plending.auth.service;

import com.p2plending.auth.domain.entity.KycSubmission;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.repository.KycSubmissionRepository;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.dto.response.InternalUserSummaryResponse;
import com.p2plending.auth.dto.response.InternalUserStatsResponse;
import com.p2plending.auth.dto.response.PagedResponse;
import com.p2plending.auth.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InternalUserQueryService {

    private final UserRepository userRepository;
    private final KycSubmissionRepository kycSubmissionRepository;

    @Transactional(readOnly = true)
    public PagedResponse<InternalUserSummaryResponse> getUsers(
            KycStatus kycStatus, String role, String search, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var users = userRepository.findAll((root, query, cb) -> {
            var predicates = cb.conjunction();
            predicates = cb.and(predicates, cb.isFalse(root.get("isDeleted")));

            if (kycStatus != null) {
                predicates = cb.and(predicates, cb.equal(root.get("kycStatus"), kycStatus));
            }
            if (StringUtils.hasText(search)) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates = cb.and(predicates, cb.or(
                        cb.like(cb.lower(root.get("phone")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)
                ));
            }
            return predicates;
        }, pageable).map(this::toSummary);
        return PagedResponse.from(users);
    }

    @Transactional(readOnly = true)
    public InternalUserSummaryResponse getUser(String userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .map(this::toSummary)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    @Transactional(readOnly = true)
    public InternalUserStatsResponse getStats(LocalDate from) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime todayStart = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        long totalUsers    = userRepository.countAllActive();
        long pendingKyc    = userRepository.countByKycStatus(KycStatus.PENDING);
        long newUsersToday = userRepository.countCreatedBetween(todayStart, tomorrowStart);

        List<Object[]> rows = userRepository.countDailyNewUsers(fromDt);
        List<InternalUserStatsResponse.DailyCount> daily = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate date = LocalDate.parse(row[0].toString());
            long count = ((Number) row[1]).longValue();
            daily.add(new InternalUserStatsResponse.DailyCount(date, count));
        }

        return InternalUserStatsResponse.builder()
                .totalUsers(totalUsers)
                .pendingKyc(pendingKyc)
                .newUsersToday(newUsersToday)
                .dailyCounts(daily)
                .build();
    }

    private InternalUserSummaryResponse toSummary(User user) {
        var approvedKyc = kycSubmissionRepository
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), KycStatus.APPROVED);
        var kyc = approvedKyc.or(() -> kycSubmissionRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId()));

        return InternalUserSummaryResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(kyc.map(KycSubmission::getFullName).orElse(null))
                .cccdNumber(kyc.map(KycSubmission::getCccdNumber).orElse(null))
                .phone(user.getPhone())
                .role("USER")
                .kycStatus(user.getKycStatus())
                .accountStatus(user.isDeleted() ? "LOCKED" : "ACTIVE")
                .createdAt(user.getCreatedAt())
                .dateOfBirth(kyc.map(KycSubmission::getDateOfBirth).orElse(null))
                .gender(kyc.map(KycSubmission::getGender).map(Enum::name).orElse(null))
                .permanentAddress(kyc.map(KycSubmission::getPermanentAddress).orElse(null))
                .hometown(kyc.map(KycSubmission::getHometown).orElse(null))
                .issueDate(kyc.map(KycSubmission::getIssueDate).orElse(null))
                .issuingAuthority(kyc.map(KycSubmission::getIssuingAuthority).orElse(null))
                .expiryDate(kyc.map(KycSubmission::getExpiryDate).orElse(null))
                .frontImageId(kyc.map(KycSubmission::getFrontImageId).orElse(null))
                .backImageId(kyc.map(KycSubmission::getBackImageId).orElse(null))
                .portraitImageId(kyc.map(KycSubmission::getPortraitImageId).orElse(null))
                .build();
    }
}
