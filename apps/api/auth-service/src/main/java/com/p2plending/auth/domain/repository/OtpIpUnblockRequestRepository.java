package com.p2plending.auth.domain.repository;

import com.p2plending.auth.domain.entity.OtpIpUnblockRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpIpUnblockRequestRepository extends JpaRepository<OtpIpUnblockRequest, String> {
    Page<OtpIpUnblockRequest> findByStatusAndIsDeletedFalseOrderByCreatedAtDesc(String status, Pageable pageable);
    Optional<OtpIpUnblockRequest> findFirstByIpAddressAndPhoneAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(String ipAddress, String phone, String status);
}
