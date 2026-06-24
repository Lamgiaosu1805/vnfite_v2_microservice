package com.p2plending.payment.domain.repository;

import com.p2plending.payment.domain.entity.ReconciliationItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReconciliationItemRepository extends JpaRepository<ReconciliationItem, String> {
    Page<ReconciliationItem> findBySessionIdAndIsDeletedFalseOrderByCreatedAtDesc(String sessionId, Pageable pageable);
    Page<ReconciliationItem> findBySessionIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(
            String sessionId, String status, Pageable pageable);

    @Query("SELECT COUNT(i) FROM ReconciliationItem i WHERE i.sessionId = :sessionId AND i.status = 'OPEN' AND i.isDeleted = false")
    int countOpenItems(@Param("sessionId") String sessionId);

    @Modifying
    @Query("UPDATE ReconciliationItem i SET i.status = :status, i.resolvedBy = :resolvedBy, i.resolvedAt = CURRENT_TIMESTAMP, i.resolutionNotes = :notes WHERE i.id = :id AND i.isDeleted = false")
    int resolve(@Param("id") String id, @Param("status") String status, @Param("resolvedBy") String resolvedBy, @Param("notes") String notes);
}
