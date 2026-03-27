package com.network.loadgen.demo;

import com.network.loadgen.config.LoadGenProperties;
import com.network.loadgen.report.ResultReporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class MetricsProxy {

    private final WebClient webClient;

    public MetricsProxy(LoadGenProperties properties) {
        this.webClient = WebClient.builder()
                .baseUrl(properties.getGatewayUrl())
                .build();
    }

    public Mono<Map<String, Object>> fetchMetrics() {
        return webClient.get()
                .uri("/actuator/prometheus")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(3))
                .map(this::parsePrometheus)
                .onErrorReturn(Map.of("error", "metrics unavailable"));
    }

    private Map<String, Object> parsePrometheus(String body) {
        return Map.of(
                "inflight", ResultReporter.parseMetric(body, "session_inflight"),
                "queueDepth", ResultReporter.parseMetric(body, "queue_depth"),
                "timeoutCount", ResultReporter.parseMetric(body, "tx_timeout_count_total"),
                "busyRejectCount", ResultReporter.parseMetric(body, "tx_busy_reject_count_total"),
                "errorCount", ResultReporter.parseMetric(body, "tx_error_count_total"),
                "lateResponseCount", ResultReporter.parseMetric(body, "tx_late_response_count_total"),
                "reconnectCount", ResultReporter.parseMetric(body, "session_reconnect_count_total")
        );
    }
}
