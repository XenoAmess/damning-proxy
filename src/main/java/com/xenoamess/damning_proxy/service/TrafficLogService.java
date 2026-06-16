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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class TrafficLogService {

    @Inject
    LogRepository logRepository;

    @Inject
    ObjectMapper objectMapper;

    private static final int MAX_BODY_LENGTH = 10000;
    private static final int MAX_HEADERS_LENGTH = 2000;
    private static final int MAX_PLUGIN_LOGS_LENGTH = 5000;
    private static final int MAX_FRIENDLY_SNAPSHOTS_LENGTH = 8000;

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
            String json = objectMapper.writeValueAsString(headers);
            return truncate(json, MAX_HEADERS_LENGTH);
        } catch (JsonProcessingException e) {
            Log.warn("Failed to serialize headers", e);
            return null;
        }
    }

    private String serializeBody(Object body) {
        if (body == null) {
            return null;
        }
        try {
            String json = (body instanceof String) ? (String) body : objectMapper.writeValueAsString(body);
            return truncate(json, MAX_BODY_LENGTH);
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
            return truncate(json, MAX_PLUGIN_LOGS_LENGTH);
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
            return truncate(json, MAX_FRIENDLY_SNAPSHOTS_LENGTH);
        } catch (JsonProcessingException e) {
            Log.warn("Failed to serialize friendly snapshots", e);
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...[truncated]";
    }
}
