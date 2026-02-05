package com.network.common.protocol;

import java.util.concurrent.atomic.AtomicLong;

public class CorrelationIdGenerator {

    private final AtomicLong counter = new AtomicLong(0);

    public String next() {
        long value = counter.incrementAndGet();
        return String.format("%08d", value % 100_000_000);
    }
}
