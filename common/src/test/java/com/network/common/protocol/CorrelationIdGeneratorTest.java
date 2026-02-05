package com.network.common.protocol;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdGeneratorTest {

    @Test
    void next_returns8CharString() {
        CorrelationIdGenerator gen = new CorrelationIdGenerator();
        String id = gen.next();
        assertThat(id).hasSize(8);
        assertThat(id).isEqualTo("00000001");
    }

    @Test
    void next_incrementsSequentially() {
        CorrelationIdGenerator gen = new CorrelationIdGenerator();
        assertThat(gen.next()).isEqualTo("00000001");
        assertThat(gen.next()).isEqualTo("00000002");
        assertThat(gen.next()).isEqualTo("00000003");
    }

    @Test
    void next_threadSafe_noDuplicates() throws InterruptedException {
        CorrelationIdGenerator gen = new CorrelationIdGenerator();
        int threadCount = 10;
        int idsPerThread = 1000;
        Set<String> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threadCount);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    for (int i = 0; i < idsPerThread; i++) {
                        ids.add(gen.next());
                    }
                    latch.countDown();
                });
            }
            latch.await();
        }

        assertThat(ids).hasSize(threadCount * idsPerThread);
    }
}
