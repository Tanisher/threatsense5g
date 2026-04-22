package com.threatsense.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;

@Component
public class MLServiceLauncher implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(MLServiceLauncher.class);

    @Value("${ml.service.autostart:true}")
    private boolean mlServiceAutostart;

    private Process mlProcess;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!mlServiceAutostart) {
            logger.info("ML service autostart is disabled via configuration.");
            return;
        }

        if (mlProcess != null && mlProcess.isAlive()) {
            logger.info("ML service process is already running; skipping launch.");
            return;
        }

        String userDir = System.getProperty("user.dir");
        File projectRoot = new File(userDir);
        File mlServiceDir = new File(projectRoot, "ml-service");

        if (!mlServiceDir.exists() || !mlServiceDir.isDirectory()) {
            logger.warn("ml-service directory not found at {}", mlServiceDir.getAbsolutePath());
            return;
        }

        String osName = System.getProperty("os.name").toLowerCase();
        String pythonCommand = osName.contains("win") ? "python" : "python3";

        File logFile = new File(projectRoot, "ml-service.log");

        ProcessBuilder builder = new ProcessBuilder(pythonCommand, "app.py");
        builder.directory(mlServiceDir);
        builder.redirectErrorStream(true);
        builder.redirectOutput(logFile);

        try {
            logger.info("Starting ML service using '{}' in directory {}", pythonCommand, mlServiceDir.getAbsolutePath());
            mlProcess = builder.start();
        } catch (IOException ex) {
            logger.error("Failed to start ML service process.", ex);
            return;
        }

        try {
            Thread.sleep(Duration.ofSeconds(3).toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        if (checkHealth()) {
            logger.info("ML Service started successfully on port 5001.");
        } else {
            logger.warn("ML Service may not have started — check ml-service.log");
        }
    }

    private boolean checkHealth() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://localhost:5001/api/ml/health");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
            connection.setReadTimeout((int) Duration.ofSeconds(2).toMillis());
            int status = connection.getResponseCode();
            return status == HttpURLConnection.HTTP_OK;
        } catch (IOException ex) {
            logger.debug("Health check for ML service failed: {}", ex.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        if (mlProcess != null && mlProcess.isAlive()) {
            logger.info("Shutting down ML service process.");
            mlProcess.destroy();
        }
    }
}

