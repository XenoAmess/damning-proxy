package com.xenoamess.damning_proxy.proxy;

import com.xenoamess.damning_proxy.entity.ProxyProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void shouldReturnOpenAfterHalfOpenProbeFailure() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker();
        ProxyProfile profile = profile(1, 1);
        String url = "http://test";
        cb.allowRequest(url);
        cb.recordFailure(url, profile);
        assertFalse(cb.allowRequest(url));

        Thread.sleep(1100);
        assertTrue(cb.allowRequest(url));
        cb.recordFailure(url, profile);
        assertFalse(cb.allowRequest(url));
    }

    @Test
    void shouldResetFailureCountOnSuccessInClosedState() {
        CircuitBreaker cb = new CircuitBreaker();
        ProxyProfile profile = profile(3, 30);
        String url = "http://test";
        cb.allowRequest(url);
        cb.recordFailure(url, profile);
        cb.allowRequest(url);
        cb.recordFailure(url, profile);
        cb.recordSuccess(url);
        cb.allowRequest(url);
        cb.recordFailure(url, profile);
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

    @Test
    void shouldReturnSnapshot() {
        CircuitBreaker cb = new CircuitBreaker();
        String url = "http://test";
        cb.allowRequest(url);

        Map<String, Map<String, Object>> snapshot = cb.getSnapshot();

        assertTrue(snapshot.containsKey(url));
        assertEquals("closed", snapshot.get(url).get("state"));
        assertEquals(0, snapshot.get(url).get("failureCount"));
    }

    @Test
    void shouldRecordFailureWithoutProfile() {
        CircuitBreaker cb = new CircuitBreaker();
        String url = "http://test";
        cb.allowRequest(url);
        cb.recordFailure(url);
        cb.allowRequest(url);
        cb.recordFailure(url);
        cb.allowRequest(url);
        cb.recordFailure(url);
        assertFalse(cb.allowRequest(url));
    }

    @Test
    void shouldRecordSuccessForUnknownUrl() {
        CircuitBreaker cb = new CircuitBreaker();
        cb.recordSuccess("http://unknown");
    }

    @Test
    void shouldReturnEmptySnapshotForFreshBreaker() {
        CircuitBreaker cb = new CircuitBreaker();
        assertTrue(cb.getSnapshot().isEmpty());
    }

    private ProxyProfile profile(int threshold, int timeoutSeconds) {
        ProxyProfile p = new ProxyProfile();
        p.circuitBreakerFailureThreshold = threshold;
        p.circuitBreakerOpenTimeoutSeconds = timeoutSeconds;
        return p;
    }
}
