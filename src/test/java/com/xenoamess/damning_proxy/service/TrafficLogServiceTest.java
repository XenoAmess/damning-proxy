package com.xenoamess.damning_proxy.service;

import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.entity.TrafficLog;
import com.xenoamess.damning_proxy.repository.LogRepository;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TrafficLogServiceTest {

    @Inject
    TrafficLogService trafficLogService;

    @Inject
    LogRepository logRepository;

    @Inject
    ProfileRepository profileRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        logRepository.deleteAll();
        profileRepository.listAll().forEach(p -> profileRepository.deleteById(p.id));
    }

    @Test
    void shouldRecordRequestAndResponse() {
        TrafficLog log = trafficLogService.recordRequest(
            1L, "/v1/chat/completions", "POST",
            Map.of("Authorization", "Bearer test"),
            Map.of("model", "gpt-4")
        );
        assertNotNull(log.id);
        assertEquals("POST", log.requestMethod);

        trafficLogService.recordResponse(log, 200,
            Map.of("Content-Type", "application/json"),
            Map.of("id", "chatcmpl-1"),
            150L, java.util.List.of("plugin log"));

        TrafficLog found = logRepository.findById(log.id).orElseThrow();
        assertEquals(200, found.responseStatus);
        assertEquals(150L, found.durationMs);
        assertNotNull(found.responseBody);
        assertTrue(found.pluginLogs.contains("plugin log"));
    }

    @Test
    void shouldTruncateLargeBody() {
        StringBuilder large = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            large.append("a");
        }

        TrafficLog log = trafficLogService.recordRequest(
            1L, "/v1/chat/completions", "POST", Map.of(), large.toString()
        );

        assertTrue(log.requestBody.length() < 15000);
        assertTrue(log.requestBody.endsWith("...[truncated]"));
    }
}
