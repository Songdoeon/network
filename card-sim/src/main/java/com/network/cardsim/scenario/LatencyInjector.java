package com.network.cardsim.scenario;

import com.network.cardsim.config.SimulatorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
@Component
public class LatencyInjector {

    private final SimulatorProperties properties;

    public long calculateDelay() {
        int base = properties.getBaseLatencyMs();
        int p95 = properties.getP95LatencyMs();

        // 정규분포 기반 지연 계산
        double gaussian = ThreadLocalRandom.current().nextGaussian();
        double stddev = (p95 - base) / 1.645; // 95th percentile ≈ mean + 1.645*stddev
        long delay = (long) (base + gaussian * stddev);
        return Math.max(1, delay);
    }
}
