package com.threatsense.scheduler;

import com.threatsense.dto.MlResponseDto;
import com.threatsense.model.Alert;
import com.threatsense.model.NetworkTraffic;
import com.threatsense.model.ThreatDetection;
import com.threatsense.model.enums.AlertStatus;
import com.threatsense.model.enums.Severity;
import com.threatsense.repository.AlertRepository;
import com.threatsense.repository.NetworkTrafficRepository;
import com.threatsense.repository.ThreatDetectionRepository;
import com.threatsense.service.MlIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ThreatDetectionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ThreatDetectionScheduler.class);

    private final NetworkTrafficRepository networkTrafficRepository;
    private final ThreatDetectionRepository threatDetectionRepository;
    private final AlertRepository alertRepository;
    private final MlIntegrationService mlIntegrationService;

    @Value("${scheduler.batch.size:500}")
    private int batchSize;

    public ThreatDetectionScheduler(NetworkTrafficRepository networkTrafficRepository,
                                    ThreatDetectionRepository threatDetectionRepository,
                                    AlertRepository alertRepository,
                                    MlIntegrationService mlIntegrationService) {
        this.networkTrafficRepository = networkTrafficRepository;
        this.threatDetectionRepository = threatDetectionRepository;
        this.alertRepository = alertRepository;
        this.mlIntegrationService = mlIntegrationService;
    }

    @Scheduled(fixedDelayString = "${scheduler.fixed.delay:60000}")
    public void runDetection() {
        Page<NetworkTraffic> page = networkTrafficRepository.findByProcessedFalse(
                PageRequest.of(0, batchSize)
        );

        if (page.isEmpty()) {
            logger.info("No unprocessed traffic records found");
            return;
        }

        List<NetworkTraffic> records = page.getContent();
        logger.info("Running ML detection for {} traffic records", records.size());

        List<MlResponseDto> responses = mlIntegrationService.analyseBatch(records);

        if (responses.isEmpty() || responses.size() != records.size()) {
            logger.warn("ML response size mismatch or empty. Expected {}, got {}",
                    records.size(), responses.size());
            return;
        }

        int processedCount = 0;
        int alertCount = 0;

        for (int i = 0; i < records.size(); i++) {
            NetworkTraffic traffic = records.get(i);
            MlResponseDto response = responses.get(i);

            Severity severity = mapSeverity(response.getSeverity());

            ThreatDetection detection = ThreatDetection.builder()
                    .traffic(traffic)
                    .threatType(response.getThreatType())
                    .severity(severity)
                    .confidenceScore(toBigDecimal(response.getConfidenceScore()))
                    .modelUsed("RF+IF")
                    .explanation(response.getExplanation())
                    .build();

            detection = threatDetectionRepository.save(detection);

            traffic.setProcessed(true);
            networkTrafficRepository.save(traffic);
            processedCount++;

            if (severity == Severity.HIGH || severity == Severity.CRITICAL) {
                Alert alert = Alert.builder()
                        .detection(detection)
                        .status(AlertStatus.OPEN)
                        .emailSent(false)
                        .build();
                alertRepository.save(alert);
                alertCount++;
            }
        }

        logger.info("Detection run complete: {} records processed, {} alerts created",
                processedCount, alertCount);
    }

    private Severity mapSeverity(String severity) {
        if (severity == null) {
            return Severity.MEDIUM;
        }
        try {
            return Severity.valueOf(severity.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Severity.MEDIUM;
        }
    }

    private BigDecimal toBigDecimal(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value);
    }
}

