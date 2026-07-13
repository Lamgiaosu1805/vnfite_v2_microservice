package com.p2plending.auth.domain.repository;

import com.p2plending.auth.domain.entity.OtpIpBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpIpBlockRepository extends JpaRepository<OtpIpBlock, String> {
    Optional<OtpIpBlock> findByIpAddressAndIsDeletedFalse(String ipAddress);
}
