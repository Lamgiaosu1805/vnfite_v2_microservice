package com.p2plending.auth.domain.repository;

import com.p2plending.auth.domain.entity.UserFcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
