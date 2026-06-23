package com.p2plending.payment.domain.repository;

import com.p2plending.payment.domain.entity.WithdrawalRequest;
import com.p2plending.payment.domain.enums.WithdrawalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, String> {

    /** Kiểm tra user có đang có withdrawal chưa kết thúc không */
    boolean existsByUserIdAndStatusInAndIsDeletedFalse(String userId, Set<WithdrawalStatus> statuses);

    Optional<WithdrawalRequest> findByIdAndUserIdAndIsDeletedFalse(String id, String userId);

    Optional<WithdrawalRequest> findByIdAndIsDeletedFalse(String id);

    Optional<WithdrawalRequest> findByTransferRefAndIsDeletedFalse(String transferRef);

    /** CMS/Ops: xem danh sách theo trạng thái (monitoring) */
    Page<WithdrawalRequest> findByStatusInAndIsDeletedFalseOrderByCreatedAtDesc(
            Set<WithdrawalStatus> statuses, Pageable pageable);

    /** Tính tổng amount đã rút trong ngày (chỉ COMPLETED) */
    @Query("""
        SELECT COALESCE(SUM(w.amount), 0)
        FROM WithdrawalRequest w
        WHERE w.userId = :userId
          AND w.status = 'COMPLETED'
          AND w.createdAt >= :startOfDay
          AND w.isDeleted = false
        """)
    BigDecimal sumCompletedAmountToday(@Param("userId") String userId,
                                       @Param("startOfDay") LocalDateTime startOfDay);

    /** Đếm số giao dịch đã rút thành công trong ngày */
    @Query("""
        SELECT COUNT(w)
        FROM WithdrawalRequest w
        WHERE w.userId = :userId
          AND w.status = 'COMPLETED'
          AND w.createdAt >= :startOfDay
          AND w.isDeleted = false
        """)
    long countCompletedToday(@Param("userId") String userId,
                             @Param("startOfDay") LocalDateTime startOfDay);

    /** Reconciliation: withdrawal kẹt ở trạng thái đang xử lý quá lâu */
    @Query("""
        SELECT w
        FROM WithdrawalRequest w
        WHERE w.status IN :statuses
          AND w.updatedAt < :threshold
          AND w.isDeleted = false
        """)
    List<WithdrawalRequest> findStuckWithdrawals(
            @Param("statuses") List<WithdrawalStatus> statuses,
            @Param("threshold") LocalDateTime threshold);
}
