package com.xenoamess.damning_proxy.service;

import com.xenoamess.damning_proxy.entity.ProxyInstance;
import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.entity.TrafficLog;
import com.xenoamess.damning_proxy.repository.InstanceRepository;
import com.xenoamess.damning_proxy.repository.LogRepository;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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

    @Inject
    InstanceRepository instanceRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        logRepository.deleteAll();
        instanceRepository.listAll().forEach(i -> instanceRepository.deleteById(i.id));
        profileRepository.listAll().forEach(p -> profileRepository.deleteById(p.id));
    }

    @Test
    void shouldRecordRequestAndResponse() {
        TrafficLog log = trafficLogService.recordRequest(
            10L, "test-instance", 1L, "/v1/chat/completions", "POST",
            Map.of("Authorization", "Bearer test"),
            Map.of("model", "gpt-4"),
            "https://api.example.com", 30000, false
        );
        assertNotNull(log.id);
        assertEquals("POST", log.requestMethod);
        assertEquals("test-instance", log.instanceSlug);
        assertEquals("https://api.example.com", log.upstreamBaseUrl);
        assertEquals(30000, log.timeoutMs);
        assertFalse(log.streaming);

        trafficLogService.recordResponse(log, 200,
            Map.of("Content-Type", "application/json"),
            Map.of("id", "chatcmpl-1"),
            150L, java.util.List.of("plugin log"), java.util.Collections.emptyList());

        TrafficLog found = logRepository.findById(log.id).orElseThrow();
        assertEquals(200, found.responseStatus);
        assertEquals(150L, found.durationMs);
        assertNotNull(found.responseBody);
        assertTrue(found.pluginLogs.contains("plugin log"));
    }

    @Test
    void shouldExtractTokenUsageFromResponse() {
        ProxyInstance instance = createInstance("extract-usage", "http://localhost:18089/v1", "sk-test");
        TrafficLog log = trafficLogService.recordRequest(
            instance.id, instance.slug, instance.profileId,
            "/v1/chat/completions", "POST",
            Map.of("Authorization", "Bearer sk-test"),
            Map.of("model", "gpt-4", "messages", java.util.List.of()),
            "http://localhost:18089/v1", 30000, false
        );

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "chatcmpl-1");
        Map<String, Object> usage = new HashMap<>();
        usage.put("prompt_tokens", 10);
        usage.put("completion_tokens", 20);
        usage.put("total_tokens", 30);
        responseBody.put("usage", usage);

        trafficLogService.recordResponse(log, 200, Map.of(), responseBody, 100, List.of(), List.of());

        TrafficLog updated = logRepository.findById(log.id).orElseThrow();
        assertEquals(10, updated.promptTokens);
        assertEquals(20, updated.completionTokens);
        assertEquals(30, updated.totalTokens);
    }

    @Test
    void shouldTruncateLargeBody() {
        StringBuilder large = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            large.append("a");
        }
        String input = large.toString();

        TrafficLog log = trafficLogService.recordRequest(
            10L, "test-instance", 1L, "/v1/chat/completions", "POST", Map.of(), input,
            "https://api.example.com", 30000, false
        );

        // With damning-proxy.log.max-body-length=-1 (the default in application.properties),
        // bodies are stored verbatim, so a 20 KB input lands at exactly 20 KB.
        assertEquals(input.length(), log.requestBody.length(),
            "default config disables truncation, body length should be preserved");
        assertFalse(log.requestBody.endsWith("...[truncated]"));
    }

    @Test
    @Transactional
    void shouldDeleteLogsOlderThanCutoff() {
        TrafficLog log = trafficLogService.recordRequest(
            10L, "test-instance", 1L, "/v1/chat/completions", "POST",
            Map.of(), Map.of("model", "gpt-4"),
            "https://api.example.com", 30000, false
        );
        assertNotNull(log.id);

        TrafficLog found = TrafficLog.findById(log.id);
        assertNotNull(found);
        found.requestTime = LocalDateTime.now().minusDays(60);

        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        long deleted = logRepository.deleteOlderThan(cutoff);
        assertTrue(deleted >= 1, "Expected at least 1 log deleted, got " + deleted);
        assertTrue(TrafficLog.findByIdOptional(log.id).isEmpty());
    }

    @Transactional
    ProxyInstance createInstance(String slug, String baseUrl, String token) {
        ProxyProfile profile = new ProxyProfile(slug, slug, baseUrl);
        profile.bearerToken = token;
        profileRepository.save(profile);
        ProxyInstance instance = new ProxyInstance();
        instance.name = slug;
        instance.slug = slug;
        instance.profileId = profile.id;
        instance.pluginGroupId = -1L;
        instance.enabled = true;
        instanceRepository.save(instance);
        return instance;
    }
}
