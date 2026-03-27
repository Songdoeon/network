package com.network.loadgen.client;

import com.network.loadgen.config.LoadGenProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class CardSimClient {

    private final WebClient webClient;

    public CardSimClient(LoadGenProperties properties) {
        this.webClient = WebClient.builder()
                .baseUrl(properties.getCardSimUrl())
                .build();
    }

    public Mono<Void> updateConfig(Map<String, Object> config) {
        return webClient.put()
                .uri("/sim/config")
                .bodyValue(config)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(3))
                .doOnError(e -> log.warn("Failed to update card-sim config: {}", e.getMessage()));
    }
}
