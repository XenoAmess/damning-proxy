package com.xenoamess.damning_proxy.proxy;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class CircuitBreaker {

    private static final int FAILURE_THRESHOLD = 3;
    private static final long OPEN_TIMEOUT_SECONDS = 30;

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

        synchronized void recordFailure() {
            failureCount++;
            switch (state) {
                case CLOSED:
                    if (failureCount >= FAILURE_THRESHOLD) {
                        state = State.OPEN;
                        openUntil = Instant.now().plusSeconds(OPEN_TIMEOUT_SECONDS);
                        Log.warnf("Circuit breaker: CLOSED -> OPEN after %d consecutive failures", failureCount);
                    }
                    break;
                case HALF_OPEN:
                    state = State.OPEN;
                    openUntil = Instant.now().plusSeconds(OPEN_TIMEOUT_SECONDS);
                    Log.warnf("Circuit breaker: HALF_OPEN -> OPEN (probe failed)");
                    break;
                case OPEN:
                    openUntil = Instant.now().plusSeconds(OPEN_TIMEOUT_SECONDS);
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

    public void recordFailure(String baseUrl) {
        CircuitState circuit = circuits.computeIfAbsent(baseUrl, k -> new CircuitState());
        circuit.recordFailure();
    }
}
