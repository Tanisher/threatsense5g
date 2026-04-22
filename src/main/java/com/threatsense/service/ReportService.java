package com.threatsense.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import com.threatsense.model.Alert;
import com.threatsense.model.ThreatDetection;
import com.threatsense.model.enums.AlertStatus;
import com.threatsense.repository.AlertRepository;
import com.threatsense.repository.ThreatDetectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter REPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ThreatDetectionRepository threatDetectionRepository;
    private final AlertRepository alertRepository;

    @Value("${report.output-dir:}")
    private String reportOutputDirOverride;

    public ReportService(ThreatDetectionRepository threatDetectionRepository, AlertRepository alertRepository) {
        this.threatDetectionRepository = threatDetectionRepository;
        this.alertRepository = alertRepository;
    }

    private Path getReportsDirectory() throws IOException {
        Path dir = reportOutputDirOverride != null && !reportOutputDirOverride.isBlank()
                ? Paths.get(reportOutputDirOverride)
                : Paths.get(System.getProperty("user.dir"), "reports");
        Files.createDirectories(dir);
        return dir;
    }

    public String generatePdfReport(LocalDate from, LocalDate to) throws IOException {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<ThreatDetection> detections = threatDetectionRepository.findByDetectedAtBetweenWithTraffic(start, end);
        List<Alert> alerts = alertRepository.findByCreatedAtBetween(start, end);

        long totalTrafficAnalysed = detections.stream()
                .map(d -> d.getTraffic().getId())
                .distinct()
                .count();
        long totalThreats = detections.size();

        Map<String, Long> threatsByType = detections.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getThreatType() != null ? d.getThreatType() : "UNKNOWN",
                        Collectors.counting()));

        long openAlerts = alerts.stream().filter(a -> a.getStatus() != AlertStatus.RESOLVED).count();
        long resolvedAlerts = alerts.stream().filter(a -> a.getStatus() == AlertStatus.RESOLVED).count();

        List<Object[]> topIps = threatDetectionRepository.findTopSourceIpsByDetectedAtBetween(
                start, end, PageRequest.of(0, 10));

        Path reportsDir = getReportsDirectory();
        long ts = System.currentTimeMillis();
        String filename = "report_" + from.format(FILE_DATE) + "_" + to.format(FILE_DATE) + "_" + ts + ".pdf";
        Path filePath = reportsDir.resolve(filename);

        try (PdfWriter writer = new PdfWriter(filePath.toFile());
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            // Title page
            document.add(new Paragraph("ThreatSense5G Threat Report")
                    .setFontSize(22)
                    .setBold()
                    .setMarginBottom(8));
            document.add(new Paragraph("Date range: " + from + " to " + to).setFontSize(12));
            document.add(new Paragraph("Generated: " + LocalDateTime.now().format(REPORT_TIMESTAMP)).setFontSize(12).setMarginBottom(24));

            // Summary stats table
            document.add(new Paragraph("Summary").setFontSize(14).setBold().setMarginBottom(8));
            Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
            summaryTable.addHeaderCell(new Cell().add(new Paragraph("Metric")).setBackgroundColor(ColorConstants.LIGHT_GRAY));
            summaryTable.addHeaderCell(new Cell().add(new Paragraph("Value")).setBackgroundColor(ColorConstants.LIGHT_GRAY));
            summaryTable.addCell("Total traffic analysed");
            summaryTable.addCell(String.valueOf(totalTrafficAnalysed));
            summaryTable.addCell("Total threats");
            summaryTable.addCell(String.valueOf(totalThreats));
            for (String type : Arrays.asList("NORMAL", "DDOS", "INTRUSION", "ANOMALY")) {
                summaryTable.addCell("Threats (" + type + ")");
                summaryTable.addCell(String.valueOf(threatsByType.getOrDefault(type, 0L)));
            }
            summaryTable.addCell("Open alerts");
            summaryTable.addCell(String.valueOf(openAlerts));
            summaryTable.addCell("Resolved alerts");
            summaryTable.addCell(String.valueOf(resolvedAlerts));
            document.add(summaryTable);
            document.add(new Paragraph());

            // Top attacking source IPs
            document.add(new Paragraph("Top 10 Attacking Source IPs").setFontSize(14).setBold().setMarginBottom(8));
            Table ipTable = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
            ipTable.addHeaderCell(new Cell().add(new Paragraph("IP Address")).setBackgroundColor(ColorConstants.LIGHT_GRAY));
            ipTable.addHeaderCell(new Cell().add(new Paragraph("Threat count")).setBackgroundColor(ColorConstants.LIGHT_GRAY));
            for (Object[] row : topIps) {
                ipTable.addCell(String.valueOf(row[0]));
                ipTable.addCell(String.valueOf(row[1]));
            }
            document.add(ipTable);
            document.add(new Paragraph());

            // Alert resolution rate
            long totalAlerts = alerts.size();
            double resolutionPct = totalAlerts > 0 ? (100.0 * resolvedAlerts / totalAlerts) : 0;
            document.add(new Paragraph("Alert resolution rate").setFontSize(14).setBold().setMarginBottom(8));
            Table rateTable = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
            rateTable.addHeaderCell(new Cell().add(new Paragraph("Metric")).setBackgroundColor(ColorConstants.LIGHT_GRAY));
            rateTable.addHeaderCell(new Cell().add(new Paragraph("Value")).setBackgroundColor(ColorConstants.LIGHT_GRAY));
            rateTable.addCell("Total alerts");
            rateTable.addCell(String.valueOf(totalAlerts));
            rateTable.addCell("Resolved");
            rateTable.addCell(String.valueOf(resolvedAlerts));
            rateTable.addCell("Resolution %");
            rateTable.addCell(String.format("%.1f%%", resolutionPct));
            document.add(rateTable);
        }

        logger.info("Generated report: {}", filename);
        return filename;
    }

    public List<String> getAllReports() {
        try {
            Path dir = getReportsDirectory();
            List<String> names = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "report_*.pdf")) {
                for (Path p : stream) {
                    names.add(p.getFileName().toString());
                }
            }
            names.sort((a, b) -> {
                // Sort by timestamp at end of filename (report_from_to_ts.pdf)
                long tsA = extractTimestamp(a);
                long tsB = extractTimestamp(b);
                return Long.compare(tsB, tsA);
            });
            return names;
        } catch (IOException e) {
            logger.warn("Could not list reports directory", e);
            return Collections.emptyList();
        }
    }

    private static long extractTimestamp(String filename) {
        int lastUnderscore = filename.lastIndexOf('_');
        if (lastUnderscore < 0) return 0;
        String suffix = filename.substring(lastUnderscore + 1).replace(".pdf", "");
        try {
            return Long.parseLong(suffix);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public Path getReportPath(String filename) {
        if (filename == null || !filename.matches("report_[a-zA-Z0-9_.-]+\\.pdf")) {
            return null;
        }
        try {
            Path dir = getReportsDirectory();
            Path resolved = dir.resolve(filename).normalize();
            if (!resolved.startsWith(dir)) return null;
            return Files.exists(resolved) ? resolved : null;
        } catch (IOException e) {
            return null;
        }
    }
}
