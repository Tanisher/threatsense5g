package com.threatsense.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MlResponseDto {

    @JsonProperty("threat_type")
    private String threatType;

    private String severity;

    @JsonProperty("confidence_score")
    private Double confidenceScore;

    private String explanation;
}

