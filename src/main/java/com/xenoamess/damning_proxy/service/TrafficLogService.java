package com.xenoamess.damning_proxy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xenoamess.damning_proxy.dto.PluginExecutionSnapshot;
import com.xenoamess.damning_proxy.entity.TrafficLog;
import com.xenoamess.damning_proxy.repository.LogRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class TrafficLogService {

    @Inject
    LogRepository logRepository;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "damning-proxy.log.max-body-length", defaultValue = "1073741824")
    int maxBodyLength;

    @ConfigProperty(name = "damning-proxy.log.max-headers-length", defaultValue = "2000")
    int maxHeadersLength;

    @ConfigProperty(name = "damning-proxy.log.max-plugin-logs-length", defaultValue = "5000")
    int maxPluginLogsLength;

    @ConfigProperty(name = "damning-proxy.log.max-friendly-snapshots-length", defaultValue = "8000")
    int maxFriendlySnapshotsLength;

    @Transactional
    public TrafficLog recordRequest(Long instanceId, String instanceSlug, Long profileId, String path, String method,
                                    Map<String, String> requestHeaders, Object requestBody) {
        TrafficLog log = new TrafficLog();
        log.instanceId = instanceId;
        log.instanceSlug = instanceSlug;
        log.profileId = profileId;
        log.requestPath = path;
        log.requestMethod = method;
        log.requestHeaders = serializeHeaders(requestHeaders);
        log.requestBody = serializeBody(requestBody);
        log.requestTime = LocalDateTime.now();
        return logRepository.save(log);
    }

    @Transactional
    public void recordResponse(TrafficLog log, int statusCode,
                               Map<String, String> responseHeaders, Object responseBody,
                               long durationMs, List<String> pluginLogs,
                               List<PluginExecutionSnapshot> friendlySnapshots) {
        TrafficLog existing = logRepository.findById(log.id).orElse(null);
        if (existing == null) {
            Log.warnf("TrafficLog not found for response recording: id=%s", log.id);
            return;
        }
        existing.responseStatus = statusCode;
        existing.responseHeaders = serializeHeaders(responseHeaders);
        existing.responseBody = serializeBody(responseBody);
        existing.durationMs = durationMs;
        existing.responseTime = LocalDateTime.now();
        existing.pluginLogs = serializePluginLogs(pluginLogs);
        existing.friendlyPluginSnapshots = serializeFriendlySnapshots(friendlySnapshots);
        logRepository.save(existing);
    }

    private String serializeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            Map<String, String> safe = new HashMap<>(headers);
            safe.replaceAll((k, v) -> "Authorization".equalsIgnoreCase(k) ? maskAuth(v) : v);
            String json = objectMapper.writeValueAsString(safe);
            return truncate(json, maxHeadersLength);
        } catch (JsonProcessingException e) {
            Log.warn("Failed to serialize headers", e);
            return null;
        }
    }

    private String maskAuth(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "Bearer ***";
        }
        return "***";
    }

    private String serializeBody(Object body) {
        if (body == null) {
            return null;
        }
        try {
            String json = (body instanceof String) ? (String) body : objectMapper.writeValueAsString(body);
            return truncate(json, maxBodyLength);
        } catch (JsonProcessingException e) {
            Log.warn("Failed to serialize body", e);
            return body.toString();
        }
    }

    private String serializePluginLogs(List<String> pluginLogs) {
        if (pluginLogs == null || pluginLogs.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(pluginLogs);
            return truncate(json, maxPluginLogsLength);
        } catch (JsonProcessingException e) {
            Log.warn("Failed to serialize plugin logs", e);
            return null;
        }
    }

    private String serializeFriendlySnapshots(List<PluginExecutionSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(snapshots);
            return truncate(json, maxFriendlySnapshotsLength);
        } catch (JsonProcessingException e) {
            Log.warn("Failed to serialize friendly snapshots", e);
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (maxLength < 0 || value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...[truncated]";
    }
}
