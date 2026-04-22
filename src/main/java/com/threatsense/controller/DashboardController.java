package com.threatsense.controller;

import com.threatsense.model.ThreatDetection;
import com.threatsense.model.enums.AlertStatus;
import com.threatsense.model.enums.Severity;
import com.threatsense.repository.AlertRepository;
import com.threatsense.repository.NetworkTrafficRepository;
import com.threatsense.repository.ThreatDetectionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    private final NetworkTrafficRepository networkTrafficRepository;
    private final ThreatDetectionRepository threatDetectionRepository;
    private final AlertRepository alertRepository;

    public DashboardController(NetworkTrafficRepository networkTrafficRepository,
                               ThreatDetectionRepository threatDetectionRepository,
                               AlertRepository alertRepository) {
        this.networkTrafficRepository = networkTrafficRepository;
        this.threatDetectionRepository = threatDetectionRepository;
        this.alertRepository = alertRepository;
    }

    @GetMapping
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(23, 59, 59, 999_999_999);

        long totalTrafficRecords = networkTrafficRepository.count();
        long totalThreatsToday = threatDetectionRepository.countByDetectedAtBetween(todayStart, todayEnd);
        long openAlertsCount = alertRepository.countByStatus(AlertStatus.OPEN);
        long criticalAlertsToday = alertRepository.countByDetectionSeverityAndCreatedAtAfter(Severity.CRITICAL, todayStart);

        long countNormal = threatDetectionRepository.countByThreatType("NORMAL");
        long countDdos = threatDetectionRepository.countByThreatType("DDOS");
        long countIntrusion = threatDetectionRepository.countByThreatType("INTRUSION");
        long countAnomaly = threatDetectionRepository.countByThreatType("ANOMALY");

        LocalDateTime sevenDaysAgo = todayStart.minusDays(6);
        List<Object[]> perDay = threatDetectionRepository.countByDetectedAtPerDay(sevenDaysAgo);
        Map<LocalDate, Long> countByDate = new java.util.HashMap<>();
        for (Object[] row : perDay) {
            if (row[0] != null && row[1] != null) {
                LocalDate d = toLocalDate(row[0]);
                if (d != null) countByDate.put(d, ((Number) row[1]).longValue());
            }
        }

        DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern("EEE dd");
        List<String> last7DaysLabels = new ArrayList<>();
        List<Long> last7DaysCounts = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            last7DaysLabels.add(d.format(labelFormat));
            last7DaysCounts.add(countByDate.getOrDefault(d, 0L));
        }

        long sliceHealthEmbb = 0;
        long sliceHealthUrllc = 0;
        long sliceHealthMmtc = 0;
        for (Object[] row : threatDetectionRepository.countBySliceType()) {
            String slice = row[0] != null ? row[0].toString() : "";
            long count = ((Number) row[1]).longValue();
            if ("eMBB".equalsIgnoreCase(slice)) sliceHealthEmbb = count;
            else if ("URLLC".equalsIgnoreCase(slice)) sliceHealthUrllc = count;
            else if ("mMTC".equalsIgnoreCase(slice)) sliceHealthMmtc = count;
        }

        List<Object[]> topSourceIps = networkTrafficRepository.findTopSourceIps(PageRequest.of(0, 10));
        List<ThreatDetection> recentThreats = threatDetectionRepository
                .findTopWithTrafficOrderByDetectedAtDesc(PageRequest.of(0, 20))
                .getContent();

        model.addAttribute("totalTrafficRecords", totalTrafficRecords);
        model.addAttribute("totalThreatsToday", totalThreatsToday);
        model.addAttribute("openAlertsCount", openAlertsCount);
        model.addAttribute("criticalAlertsToday", criticalAlertsToday);
        model.addAttribute("threatsToday", totalThreatsToday);
        model.addAttribute("countNormal", countNormal);
        model.addAttribute("countDdos", countDdos);
        model.addAttribute("countIntrusion", countIntrusion);
        model.addAttribute("countAnomaly", countAnomaly);
        model.addAttribute("last7DaysLabels", last7DaysLabels);
        model.addAttribute("last7DaysCounts", last7DaysCounts);
        model.addAttribute("sliceHealthEmbb", sliceHealthEmbb);
        model.addAttribute("sliceHealthUrllc", sliceHealthUrllc);
        model.addAttribute("sliceHealthMmtc", sliceHealthMmtc);
        model.addAttribute("topSourceIps", topSourceIps);
        model.addAttribute("recentThreats", recentThreats);

        return "dashboard/index";
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Date) return ((java.sql.Date) value).toLocalDate();
        if (value instanceof LocalDate) return (LocalDate) value;
        if (value instanceof java.util.Date) return new java.sql.Timestamp(((java.util.Date) value).getTime()).toLocalDateTime().toLocalDate();
        return null;
    }
}
