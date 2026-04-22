package com.threatsense.model;

import com.threatsense.model.enums.Severity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "threat_detections")
public class ThreatDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "traffic_id", nullable = false)
    private NetworkTraffic traffic;

    @Column(length = 50)
    private String threatType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Severity severity;

    @Column(precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(length = 50)
    private String modelUsed;

    @Lob
    private String explanation;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime detectedAt;
}

