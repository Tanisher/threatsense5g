package com.threatsense.service;

import com.threatsense.model.Alert;
import com.threatsense.model.ThreatDetection;
import com.threatsense.model.enums.AlertStatus;
import com.threatsense.model.enums.Severity;
import com.threatsense.repository.AlertRepository;
import com.threatsense.repository.ThreatDetectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class AlertService {

    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final ThreatDetectionRepository threatDetectionRepository;
    private final AuditService auditService;
    private final EmailService emailService;

    public AlertService(AlertRepository alertRepository,
                        ThreatDetectionRepository threatDetectionRepository,
                        AuditService auditService,
                        EmailService emailService) {
        this.alertRepository = alertRepository;
        this.threatDetectionRepository = threatDetectionRepository;
        this.auditService = auditService;
        this.emailService = emailService;
    }

    public List<Alert> getAlertsFiltered(String status,
                                         String severity,
                                         Long assignedToId,
                                         LocalDate from,
                                         LocalDate to) {
        AlertStatus statusEnum = parseStatus(status);
        Severity severityEnum = parseSeverity(severity);
        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        LocalDateTime toTs = to != null ? to.atTime(LocalTime.MAX) : null;

        return alertRepository.searchAlerts(statusEnum, severityEnum, assignedToId, fromTs, toTs);
    }

    public Alert getAlertById(Long id) {
        return alertRepository.findAlertWithDetailsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found with id " + id));
    }

    public void updateStatus(Long alertId, AlertStatus newStatus, Long userId) {
        Alert alert = getAlertById(alertId);
        AlertStatus oldStatus = alert.getStatus();

        alert.setStatus(newStatus);
        if (newStatus == AlertStatus.RESOLVED) {
            alert.setResolvedAt(LocalDateTime.now());
        }
        alertRepository.save(alert);

        String detail = "Status changed from " + oldStatus + " to " + newStatus;
        auditService.log(userId, "ALERT_STATUS_UPDATE", "Alert", alertId, detail);
    }

    public void assignAlert(Long alertId, Long analystId, Long currentUserId) {
        Alert alert = getAlertById(alertId);
        if (analystId != null) {
            com.threatsense.model.User user = new com.threatsense.model.User();
            user.setId(analystId);
            alert.setAssignedTo(user);
        } else {
            alert.setAssignedTo(null);
        }
        alertRepository.save(alert);

        String detail = "Alert assigned to userId=" + analystId;
        auditService.log(currentUserId, "ALERT_ASSIGN", "Alert", alertId, detail);
    }

    public void addNotes(Long alertId, String notes, Long userId) {
        Alert alert = getAlertById(alertId);
        alert.setNotes(notes);
        alertRepository.save(alert);

        String detail = "Notes updated for alert.";
        auditService.log(userId, "ALERT_NOTES_UPDATE", "Alert", alertId, detail);
    }

    public long getAlertCountByStatus(String status) {
        AlertStatus statusEnum = parseStatus(status);
        if (statusEnum == null) {
            return 0L;
        }
        return alertRepository.countByStatus(statusEnum);
    }

    public void sendCriticalAlertEmail(Alert alert) {
        try {
            emailService.sendCriticalAlertNotification(alert);
        } catch (Exception ex) {
            logger.error("Failed to send critical alert email for alert {}", alert.getId(), ex);
        }
    }

    /** Creates an alert from a threat detection (manual creation for LOW/MEDIUM that didn't auto-generate). */
    public Alert createAlertFromThreat(Long threatId, Long currentUserId) {
        ThreatDetection detection = threatDetectionRepository.findByIdWithTraffic(threatId)
                .orElseThrow(() -> new IllegalArgumentException("Threat not found with id " + threatId));
        if (alertRepository.findFirstByDetection_Id(threatId).isPresent()) {
            throw new IllegalStateException("An alert already exists for this threat");
        }
        Alert alert = Alert.builder()
                .detection(detection)
                .status(AlertStatus.OPEN)
                .emailSent(false)
                .build();
        alert = alertRepository.save(alert);
        auditService.log(currentUserId, "ALERT_CREATED_FROM_THREAT", "Alert", alert.getId(), "Created from threat #" + threatId);
        return alert;
    }

    private AlertStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return AlertStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Severity parseSeverity(String severity) {
        if (severity == null || severity.isBlank() || "ALL".equalsIgnoreCase(severity)) {
            return null;
        }
        try {
            return Severity.valueOf(severity.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

