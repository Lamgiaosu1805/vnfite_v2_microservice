package com.p2plending.auth.domain.repository;

import com.p2plending.auth.domain.entity.UserFcmToken;
import com.p2plending.auth.domain.enums.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserFcmTokenRepository extends JpaRepository<UserFcmToken, String> {

    Optional<UserFcmToken> findByUserId(String userId);

    /**
     * Xóa token này khỏi bất kỳ user nào khác đang giữ nó.
     * Dùng khi user mới đăng nhập vào cùng thiết bị (trước khi lưu token mới).
     */
    @Modifying
    @Query("DELETE FROM UserFcmToken t WHERE t.fcmToken = :fcmToken AND t.userId <> :excludeUserId")
    void deleteByFcmTokenAndUserIdNot(@Param("fcmToken") String fcmToken,
                                      @Param("excludeUserId") String excludeUserId);

    /**
     * Lấy (userId, fcmToken) theo segment KYC — dùng cho bắn thông báo marketing.
     * kycStatus null = tất cả segment. Luôn loại tài khoản đã xóa/blacklist.
     */
    @Query("SELECT t.userId, t.fcmToken FROM UserFcmToken t, User u " +
           "WHERE u.id = t.userId AND u.isDeleted = false AND u.blacklisted = false " +
           "AND (:kycStatus IS NULL OR u.kycStatus = :kycStatus)")
    List<Object[]> findUserIdAndTokenBySegment(@Param("kycStatus") KycStatus kycStatus);
}
