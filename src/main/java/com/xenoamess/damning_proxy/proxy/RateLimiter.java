package com.xenoamess.damning_proxy.proxy;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class RateLimiter {

    @ConfigProperty(name = "damning-proxy.rate-limit.max-requests", defaultValue = "60")
    int maxRequests;

    @ConfigProperty(name = "damning-proxy.rate-limit.window-seconds", defaultValue = "60")
    int windowSeconds;

    private final ConcurrentHashMap<String, WindowBucket> buckets = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupExecutor;

    @PostConstruct
    void init() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limiter-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupExecutor.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.MINUTES);
    }

    @PreDestroy
    void shutdown() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdownNow();
        }
    }

    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis() / 1000;
        WindowBucket bucket = buckets.compute(key, (k, existing) -> {
            if (existing == null) {
                return new WindowBucket(now);
            }
            long windowAge = now - existing.windowStart;
            if (windowAge >= windowSeconds) {
                return new WindowBucket(now);
            }
            return existing;
        });

        int current = bucket.counter.incrementAndGet();
        if (current > maxRequests) {
            return false;
        }
        return true;
    }

    private void cleanup() {
        long now = System.currentTimeMillis() / 1000;
        Iterator<WindowBucket> it = buckets.values().iterator();
        while (it.hasNext()) {
            WindowBucket bucket = it.next();
            if (now - bucket.windowStart >= windowSeconds) {
                it.remove();
            }
        }
    }

    private static class WindowBucket {
        final long windowStart;
        final AtomicInteger counter = new AtomicInteger(0);

        WindowBucket(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
