package com.threatsense.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.threatsense.dto.TrafficIngestionResult;
import com.threatsense.dto.TrafficUploadSummaryDto;
import com.threatsense.model.NetworkTraffic;
import com.threatsense.repository.NetworkTrafficRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TrafficIngestionService {

    private static final List<String> REQUIRED_COLUMNS = List.of(
            "timestamp",
            "src_ip",
            "dst_ip",
            "protocol",
            "packet_size",
            "packet_count",
            "duration_ms",
            "slice_type"
    );

    private final NetworkTrafficRepository networkTrafficRepository;

    public TrafficIngestionService(NetworkTrafficRepository networkTrafficRepository) {
        this.networkTrafficRepository = networkTrafficRepository;
    }

    public TrafficIngestionResult parseAndSaveCSV(MultipartFile file, String uploadedByUsername) {
        TrafficIngestionResult.TrafficIngestionResultBuilder resultBuilder =
                TrafficIngestionResult.builder().rowCount(0);

        if (file == null || file.isEmpty()) {
            resultBuilder.validationErrors(List.of("Uploaded file is empty."));
            return resultBuilder.build();
        }

        List<String> validationErrors = new ArrayList<>();
        List<NetworkTraffic> toPersist = new ArrayList<>();
        int rowCount = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReaderBuilder(reader).build()) {

            String[] header = csvReader.readNext();
            if (header == null) {
                validationErrors.add("CSV file has no header row.");
                resultBuilder.validationErrors(validationErrors);
                return resultBuilder.build();
            }

            Map<String, Integer> columnIndex = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                if (header[i] != null) {
                    columnIndex.put(header[i].trim().toLowerCase(Locale.ROOT), i);
                }
            }

            for (String required : REQUIRED_COLUMNS) {
                if (!columnIndex.containsKey(required)) {
                    validationErrors.add("Missing required column: " + required);
                }
            }

            if (!validationErrors.isEmpty()) {
                resultBuilder.validationErrors(validationErrors);
                return resultBuilder.build();
            }

            String[] row;
            while ((row = csvReader.readNext()) != null) {
                if (row.length == 0 || (row.length == 1 && row[0].isBlank())) {
                    continue;
                }

                try {
                    NetworkTraffic traffic = new NetworkTraffic();
                    traffic.setTimestamp(parseTimestamp(getValue(row, columnIndex, "timestamp")));
                    traffic.setSrcIp(getValue(row, columnIndex, "src_ip"));
                    traffic.setDstIp(getValue(row, columnIndex, "dst_ip"));
                    traffic.setProtocol(getValue(row, columnIndex, "protocol"));
                    traffic.setPacketSize(parseInteger(getValue(row, columnIndex, "packet_size")));
                    traffic.setPacketCount(parseInteger(getValue(row, columnIndex, "packet_count")));
                    traffic.setDurationMs(parseLong(getValue(row, columnIndex, "duration_ms")));
                    traffic.setSliceType(getValue(row, columnIndex, "slice_type"));
                    traffic.setUploadSource(file.getOriginalFilename());
                    traffic.setProcessed(false);

                    toPersist.add(traffic);
                    rowCount++;
                } catch (Exception ex) {
                    validationErrors.add("Error parsing row " + (rowCount + 1) + ": " + ex.getMessage());
                }
            }

            if (!toPersist.isEmpty()) {
                networkTrafficRepository.saveAll(toPersist);
            }
        } catch (Exception ex) {
            validationErrors.add("Failed to read CSV file: " + ex.getMessage());
        }

        resultBuilder.rowCount(rowCount);
        resultBuilder.validationErrors(validationErrors);
        return resultBuilder.build();
    }

    public List<TrafficUploadSummaryDto> getUploadHistory() {
        List<Object[]> raw = networkTrafficRepository.findUploadHistory();
        List<TrafficUploadSummaryDto> summaries = new ArrayList<>();

        for (Object[] row : raw) {
            String uploadSource = (String) row[0];
            long count = ((Number) row[1]).longValue();
            LocalDateTime earliest = (row[2] instanceof LocalDateTime)
                    ? (LocalDateTime) row[2]
                    : null;

            summaries.add(TrafficUploadSummaryDto.builder()
                    .uploadSource(uploadSource)
                    .rowCount(count)
                    .earliestTimestamp(earliest)
                    .build());
        }

        return summaries;
    }

    public NetworkTraffic getTrafficById(Long id) {
        return networkTrafficRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("NetworkTraffic not found with id " + id));
    }

    public List<NetworkTraffic> getRecentTraffic(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        return networkTrafficRepository.findAll(
                org.springframework.data.domain.PageRequest.of(
                        0,
                        limit,
                        org.springframework.data.domain.Sort.by("timestamp").descending()
                )
        ).getContent();
    }

    private String getValue(String[] row, Map<String, Integer> indexMap, String column) {
        Integer idx = indexMap.get(column);
        if (idx == null || idx >= row.length) {
            return null;
        }
        return row[idx] != null ? row[idx].trim() : null;
    }

    private LocalDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value);
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.valueOf(value);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.valueOf(value);
    }
}

