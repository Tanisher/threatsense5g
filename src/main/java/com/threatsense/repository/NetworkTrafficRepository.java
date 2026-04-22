package com.threatsense.repository;

import com.threatsense.model.NetworkTraffic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NetworkTrafficRepository extends JpaRepository<NetworkTraffic, Long> {

    List<NetworkTraffic> findByProcessedFalse();

    long countByProcessedTrue();

    @Query("SELECT n.uploadSource, COUNT(n), MIN(n.timestamp) " +
           "FROM NetworkTraffic n " +
           "GROUP BY n.uploadSource " +
           "ORDER BY MIN(n.timestamp) DESC")
    List<Object[]> findUploadHistory();

    Page<NetworkTraffic> findByProcessedFalse(Pageable pageable);

    List<NetworkTraffic> findByUploadSourceOrderByTimestampDesc(String uploadSource);

    @Query("SELECT n.srcIp, COUNT(n) FROM NetworkTraffic n GROUP BY n.srcIp ORDER BY COUNT(n) DESC")
    List<Object[]> findTopSourceIps(Pageable pageable);
}

