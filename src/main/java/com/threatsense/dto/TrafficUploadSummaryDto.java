package com.threatsense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrafficUploadSummaryDto {

    private String uploadSource;
    private long rowCount;
    private LocalDateTime earliestTimestamp;
}

