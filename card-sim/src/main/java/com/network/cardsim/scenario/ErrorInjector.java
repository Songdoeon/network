package com.network.cardsim.scenario;

import com.network.cardsim.config.SimulatorProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class ErrorInjector {

    private final SimulatorProperties properties;

    public ErrorInjector(SimulatorProperties properties) {
        this.properties = properties;
    }

    public boolean shouldInjectError() {
        return ThreadLocalRandom.current().nextDouble() < properties.getErrorRate();
    }
}
