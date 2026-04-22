package com.threatsense.controller;

import com.threatsense.repository.AlertRepository;
import com.threatsense.repository.ThreatDetectionRepository;
import com.threatsense.service.EmailService;
import com.threatsense.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;
    private final EmailService emailService;
    private final ThreatDetectionRepository threatDetectionRepository;
    private final AlertRepository alertRepository;

    @Value("${report.weekly-email:}")
    private String weeklyEmailTo;

    public ReportController(ReportService reportService, EmailService emailService,
                            ThreatDetectionRepository threatDetectionRepository,
                            AlertRepository alertRepository) {
        this.reportService = reportService;
        this.emailService = emailService;
        this.threatDetectionRepository = threatDetectionRepository;
        this.alertRepository = alertRepository;
    }

    @GetMapping
    public String list(Model model) {
        List<String> reports = reportService.getAllReports();
        model.addAttribute("reports", reports);
        model.addAttribute("currentPage", "reports");
        return "reports/list";
    }

    @PostMapping("/generate")
    public String generate(@RequestParam("from") LocalDate from,
                           @RequestParam("to") LocalDate to,
                           RedirectAttributes redirectAttributes) {
        if (from == null || to == null || !to.isAfter(from) && !to.equals(from)) {
            redirectAttributes.addFlashAttribute("error", "Invalid date range.");
            return "redirect:/reports";
        }
        try {
            String filename = reportService.generatePdfReport(from, to);
            redirectAttributes.addFlashAttribute("success", "Report generated: " + filename);
        } catch (Exception e) {
            logger.error("Failed to generate report", e);
            redirectAttributes.addFlashAttribute("error", "Failed to generate report.");
        }
        return "redirect:/reports";
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> download(@PathVariable String filename) {
        Path path = reportService.getReportPath(filename);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            logger.warn("Download failed for {}", filename, e);
            return ResponseEntity.notFound().build();
        }
    }

    @Scheduled(cron = "0 0 8 * * MON")
    public void weeklySummaryEmail() {
        if (weeklyEmailTo == null || weeklyEmailTo.isBlank()) {
            logger.debug("Weekly summary email skipped: report.weekly-email not set");
            return;
        }
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minus(7, ChronoUnit.DAYS);
        long threatCount = threatDetectionRepository.countByDetectedAtBetween(start, end);
        long alertCount = alertRepository.findByCreatedAtBetween(start, end).size();
        Map<String, Object> stats = new HashMap<>();
        stats.put("Threats (last 7 days)", threatCount);
        stats.put("Alerts (last 7 days)", alertCount);
        stats.put("Period", start.toLocalDate() + " to " + end.toLocalDate());
        emailService.sendWeeklySummaryEmail(weeklyEmailTo, stats);
        logger.info("Weekly summary email sent to {}", weeklyEmailTo);
    }
}
