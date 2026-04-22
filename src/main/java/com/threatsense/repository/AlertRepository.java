package com.threatsense.repository;

import com.threatsense.model.Alert;
import com.threatsense.model.enums.AlertStatus;
import com.threatsense.model.enums.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    long countByStatusAndCreatedAtAfter(AlertStatus status, LocalDateTime after);

    @Query("SELECT COUNT(a) FROM Alert a JOIN a.detection d WHERE d.severity = :severity AND a.createdAt >= :after")
    long countByDetectionSeverityAndCreatedAtAfter(@Param("severity") Severity severity, @Param("after") LocalDateTime after);

    @Query("select distinct a from Alert a " +
           "join fetch a.detection d " +
           "join fetch d.traffic t " +
           "left join fetch a.assignedTo " +
           "where a.id = :id")
    Optional<Alert> findAlertWithDetailsById(@Param("id") Long id);

    List<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status);

    long countByStatus(AlertStatus status);

    List<Alert> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("select distinct a from Alert a " +
           "join fetch a.detection d " +
           "join fetch d.traffic t " +
           "left join fetch a.assignedTo u " +
           "where (:status is null or a.status = :status) " +
           "and (:severity is null or d.severity = :severity) " +
           "and (:assignedToId is null or u.id = :assignedToId) " +
           "and (:fromTs is null or a.createdAt >= :fromTs) " +
           "and (:toTs is null or a.createdAt <= :toTs) " +
           "order by a.createdAt desc")
    List<Alert> searchAlerts(
            @Param("status") AlertStatus status,
            @Param("severity") Severity severity,
            @Param("assignedToId") Long assignedToId,
            @Param("fromTs") java.time.LocalDateTime fromTs,
            @Param("toTs") java.time.LocalDateTime toTs
    );

    Optional<Alert> findFirstByDetection_Id(Long detectionId);
}

