package com.p2plending.notification.domain.repository;

import com.p2plending.notification.domain.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    Page<Notification> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(String userId, Pageable pageable);

    Optional<Notification> findByIdAndUserIdAndIsDeletedFalse(String id, String userId);
}
