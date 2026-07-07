package com.xenoamess.damning_proxy.proxy;

import org.junit.jupiter.api.Test;

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

    private RateLimiter newLimiter(int maxRequests, int windowSeconds) {
        RateLimiter limiter = new RateLimiter();
        limiter.maxRequests = maxRequests;
        limiter.windowSeconds = windowSeconds;
        return limiter;
    }
}
