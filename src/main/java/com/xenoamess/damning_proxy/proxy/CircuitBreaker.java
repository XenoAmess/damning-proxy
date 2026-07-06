package com.xenoamess.damning_proxy.proxy;

import com.xenoamess.damning_proxy.entity.ProxyProfile;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class CircuitBreaker {

    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final long DEFAULT_OPEN_TIMEOUT_SECONDS = 30;

    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private static class CircuitState {
        State state = State.CLOSED;
        int failureCount = 0;
        Instant openUntil = null;

        synchronized boolean allowRequest() {
            Instant now = Instant.now();
            switch (state) {
                case CLOSED:
                    return true;
                case OPEN:
                    if (now.isAfter(openUntil)) {
                        state = State.HALF_OPEN;
                        Log.infof("Circuit breaker for %s: OPEN -> HALF_OPEN", "");
                        return true;
                    }
                    return false;
                case HALF_OPEN:
                    return true;
                default:
                    return false;
            }
        }

        synchronized void recordSuccess() {
            if (state == State.HALF_OPEN) {
                state = State.CLOSED;
                failureCount = 0;
                Log.infof("Circuit breaker: HALF_OPEN -> CLOSED (probe succeeded)");
            } else if (state == State.CLOSED) {
                failureCount = 0;
            }
        }

        synchronized void recordFailure(ProxyProfile profile) {
            int threshold = profile != null && profile.circuitBreakerFailureThreshold != null
                ? profile.circuitBreakerFailureThreshold
                : DEFAULT_FAILURE_THRESHOLD;
            long openSeconds = profile != null && profile.circuitBreakerOpenTimeoutSeconds != null
                ? profile.circuitBreakerOpenTimeoutSeconds
                : DEFAULT_OPEN_TIMEOUT_SECONDS;
            failureCount++;
            switch (state) {
                case CLOSED:
                    if (failureCount >= threshold) {
                        state = State.OPEN;
                        openUntil = Instant.now().plusSeconds(openSeconds);
                        Log.warnf("Circuit breaker: CLOSED -> OPEN after %d consecutive failures (timeout=%ds)", failureCount, openSeconds);
                    }
                    break;
                case HALF_OPEN:
                    state = State.OPEN;
                    openUntil = Instant.now().plusSeconds(openSeconds);
                    Log.warnf("Circuit breaker: HALF_OPEN -> OPEN (probe failed, timeout=%ds)", openSeconds);
                    break;
                case OPEN:
                    openUntil = Instant.now().plusSeconds(openSeconds);
                    break;
            }
        }
    }

    private final ConcurrentHashMap<String, CircuitState> circuits = new ConcurrentHashMap<>();

    public boolean allowRequest(String baseUrl) {
        CircuitState circuit = circuits.computeIfAbsent(baseUrl, k -> new CircuitState());
        return circuit.allowRequest();
    }

    public void recordSuccess(String baseUrl) {
        CircuitState circuit = circuits.get(baseUrl);
        if (circuit != null) {
            circuit.recordSuccess();
        }
    }

    public void recordFailure(String baseUrl, ProxyProfile profile) {
        CircuitState circuit = circuits.computeIfAbsent(baseUrl, k -> new CircuitState());
        circuit.recordFailure(profile);
    }

    public void recordFailure(String baseUrl) {
        recordFailure(baseUrl, null);
    }

    public Map<String, Map<String, Object>> getSnapshot() {
        Map<String, Map<String, Object>> snapshot = new java.util.HashMap<>();
        circuits.forEach((baseUrl, state) -> {
            Map<String, Object> info = new java.util.HashMap<>();
            info.put("state", state.state.toString().toLowerCase());
            info.put("failureCount", state.failureCount);
            info.put("openUntil", state.openUntil != null ? state.openUntil.toString() : null);
            snapshot.put(baseUrl, info);
        });
        return snapshot;
    }
}
