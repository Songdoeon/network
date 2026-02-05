package com.network.cardsim.scenario;

import com.network.cardsim.config.SimulatorProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class OutOfOrderInjector {

    private final SimulatorProperties properties;

    public OutOfOrderInjector(SimulatorProperties properties) {
        this.properties = properties;
    }

    public boolean shouldReorder() {
        return ThreadLocalRandom.current().nextDouble() < properties.getOutOfOrderRate();
    }

    /**
     * out-of-order 시 추가 지연 (ms). 다른 응답보다 늦게 보내기 위해.
     */
    public long reorderDelay() {
        return 50 + ThreadLocalRandom.current().nextLong(200);
    }
}
