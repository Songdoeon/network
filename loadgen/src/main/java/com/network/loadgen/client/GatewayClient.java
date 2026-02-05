package com.network.loadgen.client;

import com.network.common.dto.AuthorizeRequest;
import com.network.common.dto.AuthorizeResponse;
import com.network.loadgen.config.LoadGenProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class GatewayClient {

    private final WebClient webClient;

    public GatewayClient(LoadGenProperties properties) {
        this.webClient = WebClient.builder()
                .baseUrl(properties.getGatewayUrl())
                .build();
    }

    public Mono<AuthorizeResponse> authorize() {
        AuthorizeRequest request = new AuthorizeRequest(
                "merchant-" + ThreadLocalRandom.current().nextInt(100),
                ThreadLocalRandom.current().nextLong(1000, 100000),
                "KRW",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 8),
                null
        );

        return webClient.post()
                .uri("/v1/authorize")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AuthorizeResponse.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> Mono.just(
                        new AuthorizeResponse(null,
                                com.network.common.dto.TransactionStatus.ERROR,
                                e.getClass().getSimpleName(),
                                0, null)));
    }
}
