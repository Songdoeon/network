package com.network.gateway.mux;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PendingMap {

    private final ConcurrentMap<String, PendingRequest> map = new ConcurrentHashMap<>();

    public void put(String correlationId, PendingRequest request) {
        map.put(correlationId, request);
    }

    public PendingRequest remove(String correlationId) {
        return map.remove(correlationId);
    }

    public PendingRequest get(String correlationId) {
        return map.get(correlationId);
    }

    public int size() {
        return map.size();
    }

    public void forEach(java.util.function.BiConsumer<String, PendingRequest> action) {
        map.forEach(action);
    }
}
