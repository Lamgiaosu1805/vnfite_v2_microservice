package com.p2plending.loan.domain.repository;

import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.domain.enums.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface LoanRequestRepository
        extends JpaRepository<LoanRequest, String>, JpaSpecificationExecutor<LoanRequest> {

    List<LoanRequest> findByBorrowerId(String borrowerId);

    List<LoanRequest> findByStatus(LoanStatus status);

    /** Khoản đang trong vòng đời trả nợ — dùng cho job DPD. */
    List<LoanRequest> findByStatusInAndIsDeletedFalse(Collection<LoanStatus> statuses);

    /** Kiểm tra borrower có đang có khoản vay ở một trong các trạng thái chặn không. */
    boolean existsByBorrowerIdAndStatusInAndIsDeletedFalse(
            String borrowerId, Collection<LoanStatus> statuses);

    /** Số khoản gọi vốn đã hoàn thành của borrower — dùng cho credit scoring. */
    long countByBorrowerIdAndStatusAndIsDeletedFalse(String borrowerId, LoanStatus status);

    @Modifying
    @Query("UPDATE LoanRequest l SET l.status = :status WHERE l.id = :id")
    int updateStatus(@Param("id") String id, @Param("status") LoanStatus status);

    // ─── Stats queries ────────────────────────────────────────────────────────

    @Query("SELECT COUNT(l) FROM LoanRequest l WHERE l.isDeleted = false")
    long countAllActive();

    @Query("SELECT COUNT(l) FROM LoanRequest l WHERE l.isDeleted = false AND l.status IN :statuses")
    long countByStatusIn(@Param("statuses") Collection<LoanStatus> statuses);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LoanRequest l " +
           "WHERE l.isDeleted = false AND l.status IN :statuses")
    BigDecimal sumAmountByStatusIn(@Param("statuses") Collection<LoanStatus> statuses);

    @Query("SELECT COUNT(l) FROM LoanRequest l " +
           "WHERE l.isDeleted = false AND l.createdAt >= :from AND l.createdAt < :to")
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LoanRequest l " +
           "WHERE l.isDeleted = false AND l.createdAt >= :from AND l.createdAt < :to")
    BigDecimal sumAmountCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Trả về [date_string, count, volume] theo ngày */
    @Query(value = "SELECT DATE(created_at) as d, COUNT(*) as cnt, COALESCE(SUM(amount), 0) as vol " +
                   "FROM loan_requests WHERE is_deleted = 0 AND created_at >= :from " +
                   "GROUP BY DATE(created_at) ORDER BY d",
           nativeQuery = true)
    List<Object[]> countDailyNewLoans(@Param("from") LocalDateTime from);
}
