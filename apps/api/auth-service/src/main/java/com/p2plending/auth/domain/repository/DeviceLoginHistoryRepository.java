package com.p2plending.auth.domain.repository;

import com.p2plending.auth.domain.entity.DeviceLoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceLoginHistoryRepository extends JpaRepository<DeviceLoginHistory, Long> {

    /**
     * Lấy 100 bản ghi gần nhất của user (để deduplicate theo deviceKey trong service layer).
     */
    List<DeviceLoginHistory> findTop100ByUserIdAndIsDeletedFalseOrderByLoginAtDesc(String userId);
}
