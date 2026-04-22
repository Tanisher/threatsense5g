package com.threatsense.controller;

import com.threatsense.model.Alert;
import com.threatsense.model.User;
import com.threatsense.model.enums.AlertStatus;
import com.threatsense.model.enums.Severity;
import com.threatsense.repository.AuditLogRepository;
import com.threatsense.repository.UserRepository;
import com.threatsense.service.AlertService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService alertService;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    public AlertController(AlertService alertService,
                           UserRepository userRepository,
                           AuditLogRepository auditLogRepository) {
        this.alertService = alertService;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @PostMapping("/create-from-threat/{threatId}")
    public String createFromThreat(@PathVariable Long threatId,
                                   @AuthenticationPrincipal UserDetails principal,
                                   RedirectAttributes redirectAttributes) {
        User currentUser = principal != null ? userRepository.findByUsername(principal.getUsername()) : null;
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        try {
            Alert alert = alertService.createAlertFromThreat(threatId, currentUserId);
            redirectAttributes.addFlashAttribute("successMessage", "Alert created from threat.");
            return "redirect:/alerts/" + alert.getId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/threats/" + threatId;
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/threats/" + threatId;
        }
    }

    @GetMapping
    public String listAlerts(@RequestParam(required = false) String status,
                             @RequestParam(required = false) String severity,
                             @RequestParam(required = false) Long assignedToId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             Model model) {
        List<Alert> alerts = alertService.getAlertsFiltered(status, severity, assignedToId, from, to);
        List<User> analysts = userRepository.findAll();

        model.addAttribute("alerts", alerts);
        model.addAttribute("analysts", analysts);
        model.addAttribute("statuses", AlertStatus.values());
        model.addAttribute("severities", Severity.values());
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterSeverity", severity);
        model.addAttribute("filterFrom", from);
        model.addAttribute("filterTo", to);
        model.addAttribute("totalCount", alerts.size());

        return "alerts/list";
    }

    @GetMapping("/{id}")
    public String alertDetail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Alert alert = alertService.getAlertById(id);
            List<User> analysts = userRepository.findAll();

            model.addAttribute("alert", alert);
            model.addAttribute("analysts", analysts);
            model.addAttribute("auditEntries",
                    auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampAsc("Alert", id));

            return "alerts/detail";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/alerts";
        }
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam("status") String status,
                               RedirectAttributes redirectAttributes) {
        AlertStatus newStatus = AlertStatus.valueOf(status);
        // In a real app, resolve current user id from security context
        Long currentUserId = null;
        alertService.updateStatus(id, newStatus, currentUserId);
        redirectAttributes.addFlashAttribute("successMessage", "Alert status updated.");
        return "redirect:/alerts/" + id;
    }

    @PostMapping("/{id}/assign")
    public String assignAlert(@PathVariable Long id,
                              @RequestParam("assignedToId") Long analystId,
                              RedirectAttributes redirectAttributes) {
        Long currentUserId = null;
        alertService.assignAlert(id, analystId, currentUserId);
        redirectAttributes.addFlashAttribute("successMessage", "Alert assignment updated.");
        return "redirect:/alerts/" + id;
    }

    @PostMapping("/{id}/notes")
    public String updateNotes(@PathVariable Long id,
                              @RequestParam("notes") String notes,
                              RedirectAttributes redirectAttributes) {
        Long currentUserId = null;
        alertService.addNotes(id, notes, currentUserId);
        redirectAttributes.addFlashAttribute("successMessage", "Alert notes updated.");
        return "redirect:/alerts/" + id;
    }

    @GetMapping("/export")
    public void exportAlerts(HttpServletResponse response) throws IOException {
        List<Alert> alerts = alertService.getAlertsFiltered(null, null, null, null, null);

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"alerts.csv\"");

        try (PrintWriter writer = response.getWriter()) {
            writer.println("id,threat_type,severity,status,assigned_to,created_at,resolved_at");
            for (Alert alert : alerts) {
                String assignedTo = alert.getAssignedTo() != null ? alert.getAssignedTo().getUsername() : "";
                String createdAt = alert.getCreatedAt() != null ? alert.getCreatedAt().toString() : "";
                String resolvedAt = alert.getResolvedAt() != null ? alert.getResolvedAt().toString() : "";
                String threatType = alert.getDetection() != null ? alert.getDetection().getThreatType() : "";
                String severity = alert.getDetection() != null ? alert.getDetection().getSeverity().name() : "";

                writer.printf("%d,%s,%s,%s,%s,%s,%s%n",
                        alert.getId(),
                        threatType,
                        severity,
                        alert.getStatus(),
                        assignedTo,
                        createdAt,
                        resolvedAt);
            }
        }
    }
}

