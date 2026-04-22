package com.threatsense.repository;

import com.threatsense.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUserIdOrderByTimestampDesc(Long userId);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampAsc(String entityType, Long entityId);

    @Query("SELECT a FROM AuditLog a LEFT JOIN FETCH a.user u " +
           "WHERE (:username is null or :username = '' or LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))) " +
           "AND (:action is null or :action = '' or a.action = :action) " +
           "AND (:from is null or a.timestamp >= :from) " +
           "AND (:to is null or a.timestamp <= :to) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> findFiltered(@Param("username") String username, @Param("action") String action,
                                 @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);
}

