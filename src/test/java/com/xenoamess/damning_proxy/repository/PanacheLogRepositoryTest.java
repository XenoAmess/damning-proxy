package com.xenoamess.damning_proxy.repository;

import com.xenoamess.damning_proxy.entity.TrafficLog;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PanacheLogRepositoryTest {

    @Inject
    LogRepository logRepository;

    @Test
    @TestTransaction
    void shouldSaveAndFindLog() {
        TrafficLog log = new TrafficLog();
        log.profileId = 1L;
        log.requestPath = "/v1/chat/completions";
        log.requestMethod = "POST";
        log.requestBody = "{}";
        log.responseStatus = 200;
        log.responseBody = "{\"ok\":true}";

        TrafficLog saved = logRepository.save(log);
        assertNotNull(saved.id);

        Optional<TrafficLog> found = logRepository.findById(saved.id);
        assertTrue(found.isPresent());
        assertEquals(200, found.get().responseStatus);
    }

    @Test
    @TestTransaction
    void shouldListRecentLogs() {
        for (int i = 0; i < 5; i++) {
            TrafficLog log = new TrafficLog();
            log.requestPath = "/v1/chat/completions";
            log.requestMethod = "POST";
            logRepository.save(log);
        }

        List<TrafficLog> recent = logRepository.listRecent(3);
        assertEquals(3, recent.size());
    }

    @Test
    @TestTransaction
    void shouldDeleteOldestLogs() {
        logRepository.deleteAll();
        for (int i = 0; i < 5; i++) {
            TrafficLog log = new TrafficLog();
            log.requestPath = "/v1/chat/completions";
            log.requestMethod = "POST";
            log.requestTime = java.time.LocalDateTime.now().plusNanos(i * 1_000_000L);
            logRepository.save(log);
        }

        logRepository.deleteOldest(3, 2);

        long remaining = logRepository.count();
        assertEquals(2, remaining);
    }
    @Test
    @TestTransaction
    void shouldClearAllLogs() {
        TrafficLog log = new TrafficLog();
        log.requestPath = "/v1";
        log.requestMethod = "GET";
        logRepository.save(log);

        long deleted = logRepository.deleteAll();
        assertTrue(deleted >= 1);
    }
}
