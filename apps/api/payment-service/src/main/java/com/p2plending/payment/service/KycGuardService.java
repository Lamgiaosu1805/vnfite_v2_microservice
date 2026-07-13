package com.p2plending.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KycGuardService {

    private final AuthServiceClient authServiceClient;

    public void requireApproved(String userId, String actionLabel) {
        if (authServiceClient.isBlacklisted(userId)) {
            throw new IllegalStateException("Tài khoản hiện bị hạn chế giao dịch. Vui lòng liên hệ VNFITE.");
        }
        if (!authServiceClient.isKycApproved(userId)) {
            throw new IllegalStateException(
                    "Vui lòng hoàn tất xác minh danh tính eKYC trước khi " + actionLabel + ".");
        }
    }
}
