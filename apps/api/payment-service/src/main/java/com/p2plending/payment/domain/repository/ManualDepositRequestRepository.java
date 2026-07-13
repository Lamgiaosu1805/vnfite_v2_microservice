package com.p2plending.payment.domain.repository;

import com.p2plending.payment.domain.entity.ManualDepositRequest;
import com.p2plending.payment.domain.enums.ManualDepositStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ManualDepositRequestRepository extends JpaRepository<ManualDepositRequest, String> {

    Page<ManualDepositRequest> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ManualDepositRequest request where request.id = :id and request.isDeleted = false")
    Optional<ManualDepositRequest> findByIdForUpdate(@Param("id") String id);

    @Query("""
            select request from ManualDepositRequest request
            where request.isDeleted = false
              and (:status is null or request.status = :status)
            order by request.createdAt desc
            """)
    Page<ManualDepositRequest> findForCms(@Param("status") ManualDepositStatus status, Pageable pageable);
}
