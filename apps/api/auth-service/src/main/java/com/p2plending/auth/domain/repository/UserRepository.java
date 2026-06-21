package com.p2plending.auth.domain.repository;

import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.enums.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isDeleted = false")
    long countAllActive();

    @Query("SELECT COUNT(u) FROM User u WHERE u.isDeleted = false AND u.kycStatus = :status")
    long countByKycStatus(@Param("status") KycStatus status);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isDeleted = false AND u.createdAt >= :from AND u.createdAt < :to")
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isDeleted = false AND u.id IN :ids")
    long countByIdInAndIsDeletedFalse(@Param("ids") Collection<String> ids);

    @Query("SELECT COUNT(u) FROM User u WHERE u.id IN :ids AND u.createdAt >= :from AND u.createdAt < :to")
    long countByIdInAndCreatedAtBetween(@Param("ids") Collection<String> ids,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    /** Trả về [date_string, count] theo ngày */
    @Query(value = "SELECT DATE(created_at) as d, COUNT(*) as cnt FROM users " +
                   "WHERE is_deleted = 0 AND created_at >= :from " +
                   "GROUP BY DATE(created_at) ORDER BY d",
           nativeQuery = true)
    List<Object[]> countDailyNewUsers(@Param("from") LocalDateTime from);
}
