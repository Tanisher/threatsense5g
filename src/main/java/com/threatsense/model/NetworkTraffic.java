package com.threatsense.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "network_traffic")
public class NetworkTraffic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private java.time.LocalDateTime timestamp;

    @Column(length = 45)
    private String srcIp;

    @Column(length = 45)
    private String dstIp;

    @Column(length = 20)
    private String protocol;

    @Column
    private Integer packetSize;

    @Column
    private Integer packetCount;

    @Column
    private Long durationMs;

    @Column(length = 20)
    private String sliceType;

    @Column(length = 100)
    private String uploadSource;

    @Column(nullable = false)
    private boolean processed = false;
}

