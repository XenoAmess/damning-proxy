package com.xenoamess.damning_proxy.proxy;

import com.xenoamess.damning_proxy.entity.ProxyProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerTest {

    @Test
    void shouldOpenAfterThresholdFailures() {
        CircuitBreaker cb = new CircuitBreaker();
        ProxyProfile profile = profile(3, 30);
        String url = "http://test";
        for (int i = 0; i < 3; i++) {
            assertTrue(cb.allowRequest(url));
            cb.recordFailure(url, profile);
        }
        assertFalse(cb.allowRequest(url));
    }

    @Test
    void shouldCloseAfterHalfOpenProbeSuccess() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker();
        ProxyProfile profile = profile(2, 1);
        String url = "http://test";
        cb.allowRequest(url);
        cb.recordFailure(url, profile);
        cb.allowRequest(url);
        cb.recordFailure(url, profile);
        assertFalse(cb.allowRequest(url));

        Thread.sleep(1100);
        assertTrue(cb.allowRequest(url));
        cb.recordSuccess(url);
        assertTrue(cb.allowRequest(url));
    }

    @Test
    void shouldUseCustomThresholds() {
        CircuitBreaker cb = new CircuitBreaker();
        ProxyProfile profile = profile(1, 30);
        String url = "http://test";
        assertTrue(cb.allowRequest(url));
        cb.recordFailure(url, profile);
        assertFalse(cb.allowRequest(url));
    }

    private ProxyProfile profile(int threshold, int timeoutSeconds) {
        ProxyProfile p = new ProxyProfile();
        p.circuitBreakerFailureThreshold = threshold;
        p.circuitBreakerOpenTimeoutSeconds = timeoutSeconds;
        return p;
    }
}
