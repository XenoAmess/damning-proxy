package com.xenoamess.damning_proxy.proxy;

import com.xenoamess.damning_proxy.entity.GlobalSettings;
import com.xenoamess.damning_proxy.repository.GlobalSettingsRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
    int defaultMaxRequests;

    @ConfigProperty(name = "damning-proxy.rate-limit.window-seconds", defaultValue = "60")
    int defaultWindowSeconds;

    @Inject
    GlobalSettingsRepository globalSettingsRepository;

    @Inject
    MeterRegistry meterRegistry;

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
        GlobalSettings settings = globalSettingsRepository.getOrCreateSingleton();
        int maxRequests = settings.maxRequestsPerWindow != null ? settings.maxRequestsPerWindow : defaultMaxRequests;
        int windowSeconds = settings.windowSeconds != null ? settings.windowSeconds : defaultWindowSeconds;

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
        boolean allowed = current <= maxRequests;
        if (meterRegistry != null) {
            Tags tags = Tags.of("instance", key, "result", allowed ? "acquired" : "rejected");
            meterRegistry.counter("damning.rate_limit.requests", tags).increment();
        }
        return allowed;
    }

    public RateLimitInfo getRateLimitInfo(String key) {
        GlobalSettings settings = globalSettingsRepository.getOrCreateSingleton();
        int maxRequests = settings.maxRequestsPerWindow != null ? settings.maxRequestsPerWindow : defaultMaxRequests;
        int windowSeconds = settings.windowSeconds != null ? settings.windowSeconds : defaultWindowSeconds;

        WindowBucket bucket = buckets.get(key);
        if (bucket == null) {
            return new RateLimitInfo(maxRequests, maxRequests, 0);
        }

        long now = System.currentTimeMillis() / 1000;
        long windowAge = now - bucket.windowStart;
        if (windowAge >= windowSeconds) {
            return new RateLimitInfo(maxRequests, maxRequests, 0);
        }

        int remaining = Math.max(0, maxRequests - bucket.counter.get());
        long resetSeconds = windowSeconds - windowAge;
        return new RateLimitInfo(maxRequests, remaining, resetSeconds);
    }

    public record RateLimitInfo(int limit, int remaining, long resetSeconds) {
    }

    private void cleanup() {
        GlobalSettings settings = globalSettingsRepository.getOrCreateSingleton();
        int windowSeconds = settings.windowSeconds != null ? settings.windowSeconds : defaultWindowSeconds;

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
