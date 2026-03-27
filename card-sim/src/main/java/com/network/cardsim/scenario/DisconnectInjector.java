package com.network.cardsim.scenario;

import com.network.cardsim.config.SimulatorProperties;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Component
public class DisconnectInjector {

    private final SimulatorProperties properties;
    private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "disconnect-injector"));
        reschedule(properties.getDisconnectEverySec());
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    public synchronized void reschedule(int intervalSec) {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
            log.info("DisconnectInjector: previous schedule cancelled");
        }
        if (intervalSec > 0) {
            scheduledTask = scheduler.scheduleAtFixedRate(this::disconnectOne, intervalSec, intervalSec, TimeUnit.SECONDS);
            log.info("DisconnectInjector enabled: every {} seconds", intervalSec);
        } else {
            log.info("DisconnectInjector disabled");
        }
    }

    public ChannelGroup getChannels() {
        return channels;
    }

    private void disconnectOne() {
        var iterator = channels.iterator();
        if (iterator.hasNext()) {
            var channel = iterator.next();
            log.warn("Injecting disconnect on channel: {}", channel.remoteAddress());
            channel.close();
        }
    }
}
