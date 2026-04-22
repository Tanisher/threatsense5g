package com.threatsense.service;

import com.threatsense.model.Alert;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendCriticalAlertNotification(Alert alert) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            // In a real system, derive recipient(s) from alert/assignedTo or configuration
            // For now, send to the configured mail username
            helper.setTo(helper.getMimeMessage().getSession().getProperty("mail.user"));
            helper.setSubject("Critical ThreatSense5G Alert #" + alert.getId());

            String createdAt = alert.getCreatedAt() != null
                    ? alert.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : "";

            String body = "<h2>Critical Alert #" + alert.getId() + "</h2>"
                    + "<p><strong>Threat type:</strong> " + alert.getDetection().getThreatType() + "</p>"
                    + "<p><strong>Severity:</strong> " + alert.getDetection().getSeverity() + "</p>"
                    + "<p><strong>Status:</strong> " + alert.getStatus() + "</p>"
                    + "<p><strong>Created at:</strong> " + createdAt + "</p>"
                    + "<p><strong>Explanation:</strong><br/>" + alert.getDetection().getExplanation() + "</p>";

            helper.setText(body, true);

            mailSender.send(message);
        } catch (Exception ex) {
            logger.error("Failed to send critical alert notification for alert {}", alert.getId(), ex);
        }
    }

    public void sendWeeklySummaryEmail(String toEmail, Map<String, Object> stats) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(toEmail);
            helper.setSubject("ThreatSense5G Weekly Alert Summary");

            StringBuilder body = new StringBuilder();
            body.append("<h2>Weekly Alert Summary</h2>");
            body.append("<ul>");
            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                body.append("<li><strong>")
                        .append(entry.getKey())
                        .append(":</strong> ")
                        .append(entry.getValue())
                        .append("</li>");
            }
            body.append("</ul>");

            helper.setText(body.toString(), true);

            mailSender.send(message);
        } catch (Exception ex) {
            logger.error("Failed to send weekly summary email to {}", toEmail, ex);
        }
    }
}

