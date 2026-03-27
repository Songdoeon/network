package com.network.cardsim.api;

import com.network.cardsim.config.SimulatorProperties;
import com.network.cardsim.scenario.DisconnectInjector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/sim")
public class SimConfigController {

    private final SimulatorProperties properties;
    private final DisconnectInjector disconnectInjector;

    @GetMapping("/config")
    public ResponseEntity<SimConfigResponse> getConfig() {
        return ResponseEntity.ok(SimConfigResponse.from(properties));
    }

    @PutMapping("/config")
    public ResponseEntity<SimConfigResponse> updateConfig(@RequestBody SimConfigRequest request) {
        if (request.baseLatencyMs() != null) {
            properties.setBaseLatencyMs(request.baseLatencyMs());
        }
        if (request.p95LatencyMs() != null) {
            properties.setP95LatencyMs(request.p95LatencyMs());
        }
        if (request.errorRate() != null) {
            properties.setErrorRate(request.errorRate());
        }
        if (request.disconnectEverySec() != null) {
            properties.setDisconnectEverySec(request.disconnectEverySec());
            disconnectInjector.reschedule(request.disconnectEverySec());
        }
        if (request.outOfOrderRate() != null) {
            properties.setOutOfOrderRate(request.outOfOrderRate());
        }

        log.info("Config updated: {}", SimConfigResponse.from(properties));
        return ResponseEntity.ok(SimConfigResponse.from(properties));
    }
}
