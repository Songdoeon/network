package com.network.cardsim.scenario;

import com.network.cardsim.config.SimulatorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
@Component
public class ErrorInjector {

    private final SimulatorProperties properties;

    public boolean shouldInjectError() {
        return ThreadLocalRandom.current().nextDouble() < properties.getErrorRate();
    }
}
