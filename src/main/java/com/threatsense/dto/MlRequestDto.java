package com.threatsense.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.threatsense.model.NetworkTraffic;
import lombok.Builder;
import lombok.Data;

@Data
public class MlRequestDto {

    private Long id;

    @JsonProperty("packet_size")
    private Integer packetSize;

    @JsonProperty("packet_count")
    private Integer packetCount;

    @JsonProperty("duration_ms")
    private Long durationMs;

    private String protocol;

    @JsonProperty("slice_type")
    private String sliceType;

    public static MlRequestDto fromNetworkTraffic(NetworkTraffic traffic) {
        MlRequestDto dto = new MlRequestDto();
        dto.setId(traffic.getId());
        dto.setPacketSize(traffic.getPacketSize());
        dto.setPacketCount(traffic.getPacketCount());
        dto.setDurationMs(traffic.getDurationMs());
        dto.setProtocol(traffic.getProtocol());
        dto.setSliceType(traffic.getSliceType());
        return dto;
    }
}

