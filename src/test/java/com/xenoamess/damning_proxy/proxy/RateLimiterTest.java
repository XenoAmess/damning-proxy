package com.xenoamess.damning_proxy.proxy;

import com.xenoamess.damning_proxy.entity.GlobalSettings;
import com.xenoamess.damning_proxy.repository.GlobalSettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void shouldAllowRequestsWithinLimit() {
        RateLimiter limiter = newLimiter(3, 60);
        assertTrue(limiter.tryAcquire("key"));
        assertTrue(limiter.tryAcquire("key"));
        assertTrue(limiter.tryAcquire("key"));
        assertFalse(limiter.tryAcquire("key"));
    }

    @Test
    void shouldIsolateKeys() {
        RateLimiter limiter = newLimiter(1, 60);
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
    }

    @Test
    void shouldReadLimitsFromGlobalSettings() {
        GlobalSettings settings = new GlobalSettings();
        settings.maxRequestsPerWindow = 2;
        settings.windowSeconds = 10;
        RateLimiter limiter = newLimiterWithSettings(settings);
        assertTrue(limiter.tryAcquire("key"));
        assertTrue(limiter.tryAcquire("key"));
        assertFalse(limiter.tryAcquire("key"));
    }

    @Test
    void shouldUpdateLimitsDynamically() {
        GlobalSettings settings = new GlobalSettings();
        settings.maxRequestsPerWindow = 1;
        settings.windowSeconds = 10;
        AtomicInteger reads = new AtomicInteger(0);
        RateLimiter limiter = newLimiterWithSettings(settings, reads);
        assertTrue(limiter.tryAcquire("key"));
        assertFalse(limiter.tryAcquire("key"));
        settings.maxRequestsPerWindow = 3;
        assertTrue(limiter.tryAcquire("key"));
        assertFalse(limiter.tryAcquire("key"));
        assertTrue(reads.get() > 0);
    }

    @Test
    void shouldReturnCorrectRateLimitInfo() {
        RateLimiter limiter = newLimiter(5, 60);
        limiter.tryAcquire("key");
        limiter.tryAcquire("key");

        RateLimiter.RateLimitInfo info = limiter.getRateLimitInfo("key");

        assertEquals(5, info.limit());
        assertEquals(3, info.remaining());
    }

    @Test
    void shouldReturnFullQuotaForUnknownKey() {
        RateLimiter limiter = newLimiter(5, 60);

        RateLimiter.RateLimitInfo info = limiter.getRateLimitInfo("unknown");

        assertEquals(5, info.limit());
        assertEquals(5, info.remaining());
        assertEquals(0, info.resetSeconds());
    }

    @Test
    void shouldBeThreadSafeUnderConcurrentAccess() throws Exception {
        RateLimiter limiter = newLimiter(1000, 60);
        int threads = 10;
        int iterations = 200;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger permitted = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                for (int i = 0; i < iterations; i++) {
                    if (limiter.tryAcquire("shared")) {
                        permitted.incrementAndGet();
                    }
                }
                latch.countDown();
            }).start();
        }
        latch.await();

        assertTrue(permitted.get() <= 1000, "should not exceed limit under concurrency");
    }

    private RateLimiter newLimiter(int maxRequests, int windowSeconds) {
        GlobalSettings settings = new GlobalSettings();
        settings.maxRequestsPerWindow = maxRequests;
        settings.windowSeconds = windowSeconds;
        return newLimiterWithSettings(settings);
    }

    private RateLimiter newLimiterWithSettings(GlobalSettings settings) {
        return newLimiterWithSettings(settings, new AtomicInteger(0));
    }

    private RateLimiter newLimiterWithSettings(GlobalSettings settings, AtomicInteger reads) {
        RateLimiter limiter = new RateLimiter();
        limiter.globalSettingsRepository = new GlobalSettingsRepository() {
            @Override
            public GlobalSettings save(GlobalSettings s) {
                return s;
            }

            @Override
            public Optional<GlobalSettings> findById(Long id) {
                return Optional.of(settings);
            }

            @Override
            public GlobalSettings getOrCreateSingleton() {
                reads.incrementAndGet();
                return settings;
            }

            @Override
            public void invalidateCache() {
            }
        };
        return limiter;
    }
}
