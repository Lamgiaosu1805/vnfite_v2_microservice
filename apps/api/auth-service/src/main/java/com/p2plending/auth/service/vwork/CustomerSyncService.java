package com.p2plending.auth.service.vwork;

import com.p2plending.auth.common.Constant;
import com.p2plending.auth.domain.entity.KycSubmission;
import com.p2plending.auth.dto.request.vwork.UpsertCustomerRequest;
import com.p2plending.auth.feign.VWorkFeignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerSyncService {
    private final VWorkFeignService vWorkFeignService;

    @Async
    public void vWorkRegister(String apiKey, String userId, String redfCode, String phone) {
        try {
            UpsertCustomerRequest upsertRequest = new UpsertCustomerRequest();
            upsertRequest.setAppCode(Constant.APP_CODE);
            upsertRequest.setPhoneNumber(phone);
            upsertRequest.setExternalId(userId);
            upsertRequest.setRefCode(redfCode);

            vWorkFeignService.upsertCustomer(apiKey, upsertRequest);
        } catch (Exception e) {
            log.error("Xảy ra ngoại lệ khi call VWork, SĐT: {}", phone, e);
        }
    }

    @Async
    public void vWorkEKYC(String apiKey, String userId, String phone, KycSubmission kycSubmission) {
        try {
            UpsertCustomerRequest upsertRequest = new UpsertCustomerRequest();
            upsertRequest.setAppCode(Constant.APP_CODE);
            upsertRequest.setPhoneNumber(phone);
            upsertRequest.setExternalId(userId);
            upsertRequest.setFullName(kycSubmission.getFullName());
            upsertRequest.setLegalId(kycSubmission.getCccdNumber());
            upsertRequest.setTypeId(Constant.CCCD);
            upsertRequest.setBirthday(kycSubmission.getDateOfBirth());
            upsertRequest.setGender(kycSubmission.getGender());
            upsertRequest.setLegalIssueDate(kycSubmission.getIssueDate());
            upsertRequest.setLegalPlace(kycSubmission.getIssuingAuthority());
            upsertRequest.setAddress(kycSubmission.getHometown());
            upsertRequest.setFrontImgPath(kycSubmission.getFrontImageId());
            upsertRequest.setBackImgPath(kycSubmission.getBackImageId());
            upsertRequest.setSelfiePath(kycSubmission.getPortraitImageId());

            vWorkFeignService.upsertCustomer(apiKey, upsertRequest);
        } catch (Exception e) {
            log.error("Xảy ra ngoại lệ khi call VWork, SĐT: {}", phone, e);
        }
    }
}
