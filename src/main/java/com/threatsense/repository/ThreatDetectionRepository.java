package com.threatsense.repository;

import com.threatsense.model.ThreatDetection;
import com.threatsense.model.enums.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ThreatDetectionRepository extends JpaRepository<ThreatDetection, Long> {

    List<ThreatDetection> findTop20ByOrderByDetectedAtDesc();

    long countBySeverity(String severity);

    long countByThreatType(String threatType);

    long countByDetectedAtBetween(LocalDateTime start, LocalDateTime end);

    List<ThreatDetection> findByDetectedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT d FROM ThreatDetection d JOIN FETCH d.traffic t WHERE d.detectedAt BETWEEN :start AND :end")
    List<ThreatDetection> findByDetectedAtBetweenWithTraffic(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT DATE(d.detected_at) as d, COUNT(d.id) FROM threat_detections d " +
            "WHERE d.detected_at >= :start GROUP BY DATE(d.detected_at) ORDER BY d", nativeQuery = true)
    List<Object[]> countByDetectedAtPerDay(@Param("start") LocalDateTime start);

    @Query("SELECT t.sliceType, COUNT(d) FROM ThreatDetection d JOIN d.traffic t GROUP BY t.sliceType")
    List<Object[]> countBySliceType();

    @Query(value = "SELECT d FROM ThreatDetection d JOIN FETCH d.traffic t ORDER BY d.detectedAt DESC",
            countQuery = "SELECT COUNT(d) FROM ThreatDetection d")
    Page<ThreatDetection> findTopWithTrafficOrderByDetectedAtDesc(Pageable pageable);

    @Query("SELECT t.srcIp, COUNT(d) FROM ThreatDetection d JOIN d.traffic t WHERE d.detectedAt BETWEEN :start AND :end GROUP BY t.srcIp ORDER BY COUNT(d) DESC")
    List<Object[]> findTopSourceIpsByDetectedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    @Query(value = "SELECT d FROM ThreatDetection d WHERE (:threatType is null or :threatType = '' or :threatType = 'ALL' or d.threatType = :threatType) AND (:severity is null or d.severity = :severity) AND (:from is null or d.detectedAt >= :from) AND (:to is null or d.detectedAt <= :to) ORDER BY d.detectedAt DESC",
            countQuery = "SELECT COUNT(d) FROM ThreatDetection d WHERE (:threatType is null or :threatType = '' or :threatType = 'ALL' or d.threatType = :threatType) AND (:severity is null or d.severity = :severity) AND (:from is null or d.detectedAt >= :from) AND (:to is null or d.detectedAt <= :to)")
    Page<ThreatDetection> findFiltered(@Param("threatType") String threatType, @Param("severity") Severity severity,
                                        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    @Query("SELECT d FROM ThreatDetection d JOIN FETCH d.traffic t WHERE d.id = :id")
    Optional<ThreatDetection> findByIdWithTraffic(@Param("id") Long id);
}

