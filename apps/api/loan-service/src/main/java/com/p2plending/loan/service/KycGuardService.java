package com.p2plending.loan.service;

import com.p2plending.loan.client.AuthServiceClient;
import com.p2plending.loan.dto.response.InternalUserResponse;
import com.p2plending.loan.exception.InvalidLoanStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KycGuardService {

    private static final String APPROVED = "APPROVED";

    private final AuthServiceClient authServiceClient;

    public InternalUserResponse requireApproved(String userId, String actionLabel) {
        InternalUserResponse user = authServiceClient.getUserById(userId)
                .orElseThrow(() -> new InvalidLoanStateException(
                        "Không lấy được thông tin xác minh danh tính. Vui lòng thử lại."));

        if (!APPROVED.equalsIgnoreCase(user.getKycStatus())) {
            throw new InvalidLoanStateException(
                    "Vui lòng hoàn tất xác minh danh tính eKYC trước khi " + actionLabel + ".");
        }

        if (user.isBlacklisted()) {
            throw new InvalidLoanStateException(
                    "Tài khoản của bạn hiện không đủ điều kiện đăng ký gọi vốn. Vui lòng liên hệ VNFITE để được hỗ trợ.");
        }

        return user;
    }
}
