package com.p2plending.auth.domain.repository;

import com.p2plending.auth.domain.entity.UserFcmToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserFcmTokenRepository extends JpaRepository<UserFcmToken, String> {
    Optional<UserFcmToken> findByUserId(String userId);
}
