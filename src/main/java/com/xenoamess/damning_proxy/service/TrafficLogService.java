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
import java.util.concurrent.atomic.AtomicLong;

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

    @ConfigProperty(name = "damning-proxy.log.max-count", defaultValue = "100000")
    long maxLogCount;

    @ConfigProperty(name = "damning-proxy.log.prune-batch-size", defaultValue = "1000")
    int pruneBatchSize;

    private final AtomicLong recordCounter = new AtomicLong(0);
    private static final long PRUNE_INTERVAL = 100;

    @Transactional
    public TrafficLog recordRequest(Long instanceId, String instanceSlug, Long profileId, String path, String method,
                                    Map<String, String> requestHeaders, Object requestBody,
                                    String upstreamBaseUrl, int timeoutMs, boolean streaming) {
        TrafficLog log = new TrafficLog();
        log.instanceId = instanceId;
        log.instanceSlug = instanceSlug;
        log.profileId = profileId;
        log.requestPath = path;
        log.requestMethod = method;
        log.requestHeaders = serializeHeaders(requestHeaders);
        String fullBody = toJson(requestBody);
        log.requestBodyLength = fullBody != null ? fullBody.length() : null;
        log.requestBody = truncate(fullBody, maxBodyLength);
        log.upstreamBaseUrl = upstreamBaseUrl;
        log.timeoutMs = timeoutMs;
        log.streaming = streaming;
        log.requestTime = LocalDateTime.now();
        return logRepository.save(log);
    }

    @Transactional
    public void recordResponse(TrafficLog log, int statusCode,
                               Map<String, String> responseHeaders, Object responseBody,
                               long durationMs, List<String> pluginLogs,
                               List<PluginExecutionSnapshot> friendlySnapshots) {
        recordResponse(log, statusCode, responseHeaders, responseBody, durationMs, pluginLogs, friendlySnapshots, null);
    }

    @Transactional
    public void recordResponse(TrafficLog log, int statusCode,
                               Map<String, String> responseHeaders, Object responseBody,
                               long durationMs, List<String> pluginLogs,
                               List<PluginExecutionSnapshot> friendlySnapshots, String errorMessage) {
        TrafficLog existing = logRepository.findById(log.id).orElse(null);
        if (existing == null) {
            Log.warnf("TrafficLog not found for response recording: id=%s", log.id);
            return;
        }
        existing.responseStatus = statusCode;
        existing.responseHeaders = serializeHeaders(responseHeaders);
        String fullBody = toJson(responseBody);
        existing.responseBodyLength = fullBody != null ? fullBody.length() : null;
        existing.responseBody = truncate(fullBody, maxBodyLength);
        existing.durationMs = durationMs;
        existing.responseTime = LocalDateTime.now();
        existing.pluginLogs = serializePluginLogs(pluginLogs);
        existing.friendlyPluginSnapshots = serializeFriendlySnapshots(friendlySnapshots);
        existing.errorMessage = errorMessage;
        extractTokenUsage(existing, responseBody);
        logRepository.save(existing);

        if (recordCounter.incrementAndGet() % PRUNE_INTERVAL == 0) {
            pruneOldLogs();
        }
    }

    private void extractTokenUsage(TrafficLog log, Object responseBody) {
        if (!(responseBody instanceof Map)) {
            return;
        }
        Object usage = ((Map<?, ?>) responseBody).get("usage");
        if (!(usage instanceof Map)) {
            return;
        }
        Map<?, ?> u = (Map<?, ?>) usage;
        log.promptTokens = asInteger(u.get("prompt_tokens"));
        log.completionTokens = asInteger(u.get("completion_tokens"));
        log.totalTokens = asInteger(u.get("total_tokens"));
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Transactional
    public void appendPluginLog(TrafficLog log, String message) {
        TrafficLog existing = logRepository.findById(log.id).orElse(null);
        if (existing == null) {
            return;
        }
        List<String> logs = new ArrayList<>();
        if (existing.pluginLogs != null && !existing.pluginLogs.isBlank()) {
            try {
                logs = objectMapper.readValue(existing.pluginLogs, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (JsonProcessingException e) {
                logs.add(existing.pluginLogs);
            }
        }
        logs.add(LocalDateTime.now() + " " + message);
        existing.pluginLogs = serializePluginLogs(logs);
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

    private String toJson(Object body) {
        if (body == null) {
            return null;
        }
        try {
            return (body instanceof String) ? (String) body : objectMapper.writeValueAsString(body);
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

    private void pruneOldLogs() {
        long total = logRepository.count();
        if (total <= maxLogCount) {
            return;
        }
        long toDelete = total - maxLogCount;
        Log.infof("Pruning %d old traffic logs (total=%d, max=%d)", toDelete, total, maxLogCount);
        try {
            logRepository.deleteOldest((int) Math.min(toDelete, Integer.MAX_VALUE), pruneBatchSize);
        } catch (Exception e) {
            Log.error("Failed to prune old logs", e);
        }
    }
}
