package com.p2plending.auth.service.vwork;

import com.p2plending.auth.common.Constant;
import com.p2plending.auth.domain.entity.KycSubmission;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.repository.KycSubmissionRepository;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.dto.request.vwork.CustomerSyncRequest;
import com.p2plending.auth.feign.VWorkFeignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {
    private final UserRepository userRepository;
    private final KycSubmissionRepository kycSubmissionRepository;
    private final VWorkFeignService vWorkFeignService;

    @Value("${spring.vwork.api-key}")
    private String apiKey;

    private static final int BATCH_SIZE = 500;

    public Map<String, String> syncAllCustomers(String transactionId) {
        try {
            long totalUsers = userRepository.countAllActive();
            int totalBatch = (int) Math.ceil((double) totalUsers / BATCH_SIZE);

            for (int i = 0; i < totalBatch; i++) {
                Pageable pageable = PageRequest.of(i, BATCH_SIZE);
                List<User> users = userRepository.findAllUser(pageable);

                List<CustomerSyncRequest.CustomerDto> customerDtos = new ArrayList<>();
                for (User user : users) {
                    KycSubmission kycUser = kycSubmissionRepository.findByUserId(user.getId());
                    CustomerSyncRequest.CustomerDto dto = mapToDto(user, kycUser);
                    customerDtos.add(dto);
                }

                CustomerSyncRequest request = CustomerSyncRequest.builder()
                        .appCode(Constant.APP_CODE)
                        .customerDtos(customerDtos)
                        .build();

                vWorkFeignService.bulkUpsertCustomers(apiKey, request);
            }
            return Map.of("message", "Đồng bộ khách hàng sang CRM thành công!");
        } catch (Exception e) {
            log.error("transactionId: {} - Lỗi khi sync khách hàng sang CRM", transactionId, e);
            throw e;
        }
    }

    private CustomerSyncRequest.CustomerDto mapToDto(User user, KycSubmission kycSubmission) {
        boolean isKyc = kycSubmission != null && kycSubmission.getStatus() == KycStatus.APPROVED;
        CustomerSyncRequest.CustomerDto.CustomerDtoBuilder builder = CustomerSyncRequest.CustomerDto.builder()
                .phoneNumber(user.getPhone())
                .externalId(user.getId())
                .isKyc(isKyc);

        if (isKyc) {
            builder.fullName(kycSubmission.getFullName())
                    .dateOfBirth(kycSubmission.getDateOfBirth())
                    .gender(kycSubmission.getGender())
                    .idNumber(kycSubmission.getCccdNumber())
                    .idType(Constant.CCCD)
                    .idIssuedDate(kycSubmission.getIssueDate())
                    .idIssuedPlace(kycSubmission.getIssuingAuthority())
                    .address(kycSubmission.getPermanentAddress());
        }

        return builder.build();
    }
}
