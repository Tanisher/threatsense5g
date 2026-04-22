package com.threatsense.service;

import com.threatsense.dto.MlRequestDto;
import com.threatsense.dto.MlResponseDto;
import com.threatsense.model.NetworkTraffic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MlIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(MlIntegrationService.class);

    private final WebClient mlWebClient;

    public MlIntegrationService(WebClient mlWebClient) {
        this.mlWebClient = mlWebClient;
    }

    public List<MlResponseDto> analyseBatch(List<NetworkTraffic> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        List<MlRequestDto> payload = records.stream()
                .map(MlRequestDto::fromNetworkTraffic)
                .collect(Collectors.toList());

        try {
            Mono<List<MlResponseDto>> responseMono = mlWebClient.post()
                    .uri("/api/ml/analyse")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToFlux(MlResponseDto.class)
                    .collectList();

            return responseMono.block();
        } catch (Exception ex) {
            logger.error("ML analyseBatch call failed", ex);
            return Collections.emptyList();
        }
    }
}

