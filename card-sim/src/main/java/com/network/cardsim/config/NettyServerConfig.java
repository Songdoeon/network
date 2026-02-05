package com.network.cardsim.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class NettyServerConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService cardSimScheduler() {
        return Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "cardsim-delay");
            t.setDaemon(true);
            return t;
        });
    }
}
