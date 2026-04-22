package com.threatsense.controller;

import com.threatsense.model.Alert;
import com.threatsense.model.ThreatDetection;
import com.threatsense.model.enums.Severity;
import com.threatsense.repository.AlertRepository;
import com.threatsense.repository.ThreatDetectionRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

@Controller
@RequestMapping("/threats")
public class ThreatController {

    private static final String[] THREAT_TYPES = {"ALL", "NORMAL", "DDOS", "INTRUSION", "ANOMALY"};

    private final ThreatDetectionRepository threatDetectionRepository;
    private final AlertRepository alertRepository;

    public ThreatController(ThreatDetectionRepository threatDetectionRepository, AlertRepository alertRepository) {
        this.threatDetectionRepository = threatDetectionRepository;
        this.alertRepository = alertRepository;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String threatType,
                       @RequestParam(required = false) String severity,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Severity severityEnum = parseSeverity(severity);
        LocalDateTime from = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime to = dateTo != null ? dateTo.atTime(LocalTime.MAX) : null;

        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), 20);
        var threatPage = threatDetectionRepository.findFiltered(threatType, severityEnum, from, to, pageable);

        model.addAttribute("threatPage", threatPage);
        model.addAttribute("threatTypes", THREAT_TYPES);
        model.addAttribute("severities", Severity.values());
        model.addAttribute("filterThreatType", threatType != null ? threatType : "ALL");
        model.addAttribute("filterSeverity", severity);
        model.addAttribute("filterDateFrom", dateFrom);
        model.addAttribute("filterDateTo", dateTo);
        model.addAttribute("totalCount", threatPage.getTotalElements());
        return "threats/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ThreatDetection threat = threatDetectionRepository.findByIdWithTraffic(id)
                .orElseThrow(() -> new IllegalArgumentException("Threat not found with id " + id));
        Optional<Alert> linkedAlert = alertRepository.findFirstByDetection_Id(id);
        boolean canCreateAlert = linkedAlert.isEmpty()
                && (threat.getSeverity() == Severity.LOW || threat.getSeverity() == Severity.MEDIUM);

        model.addAttribute("threat", threat);
        model.addAttribute("linkedAlert", linkedAlert.orElse(null));
        model.addAttribute("canCreateAlert", canCreateAlert);
        return "threats/detail";
    }

    private static Severity parseSeverity(String severity) {
        if (severity == null || severity.isBlank() || "ALL".equalsIgnoreCase(severity)) return null;
        try {
            return Severity.valueOf(severity.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
