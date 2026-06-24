package com.p2plending.payment.domain.repository;

import com.p2plending.payment.domain.entity.ReconciliationSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ReconciliationSessionRepository extends JpaRepository<ReconciliationSession, String> {
    Page<ReconciliationSession> findByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);
    Optional<ReconciliationSession> findByReconDateAndIsDeletedFalse(LocalDate date);
}
