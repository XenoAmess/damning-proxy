package com.xenoamess.damning_proxy.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.entity.PluginGroup;
import com.xenoamess.damning_proxy.entity.PluginGroupItem;
import com.xenoamess.damning_proxy.entity.ProxyInstance;
import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.entity.TrafficLog;
import com.xenoamess.damning_proxy.plugin.PluginContext;
import com.xenoamess.damning_proxy.plugin.PluginExecutionService;
import com.xenoamess.damning_proxy.repository.InstanceRepository;
import com.xenoamess.damning_proxy.repository.PluginGroupRepository;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import com.xenoamess.damning_proxy.service.TrafficLogService;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.core.type.TypeReference;

@ApplicationScoped
public class OpenAiProxyService {

    @Inject
    InstanceRepository instanceRepository;

    @Inject
    ProfileRepository profileRepository;

    @Inject
    PluginGroupRepository pluginGroupRepository;

    @Inject
    PluginExecutionService pluginExecutionService;

    @Inject
    UpstreamHttpClient upstreamHttpClient;

    @Inject
    CircuitBreaker circuitBreaker;

    @Inject
    TrafficLogService trafficLogService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ExecutorService executorService;

    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "stream-heartbeat");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    void shutdown() {
        heartbeatScheduler.shutdownNow();
    }

    public Response listModels(String instanceSlug, jakarta.ws.rs.core.HttpHeaders incomingHeaders) {
        return proxyRequest(instanceSlug, null, "GET", "/v1/models", incomingHeaders,
            (profile, context) -> upstreamHttpClient.send(
                "GET", profile.baseUrl, "/models",
                toMultiMap(context.getRequestHeaders()), null, profile.timeoutMs,
                profile
            ));
    }

    public Response chatCompletions(String instanceSlug, Object requestBody, jakarta.ws.rs.core.HttpHeaders incomingHeaders) {
        return proxyRequest(instanceSlug, requestBody, "POST", "/v1/chat/completions", incomingHeaders,
            (profile, context) -> upstreamHttpClient.send(
                "POST", profile.baseUrl, "/chat/completions",
                toMultiMap(context.getRequestHeaders()), context.getRequestBody(), profile.timeoutMs,
                profile
            ));
    }

    public Response embeddings(String instanceSlug, Object requestBody, jakarta.ws.rs.core.HttpHeaders incomingHeaders) {
        return proxyRequest(instanceSlug, requestBody, "POST", "/v1/embeddings", incomingHeaders,
            (profile, context) -> upstreamHttpClient.send(
                "POST", profile.baseUrl, "/embeddings",
                toMultiMap(context.getRequestHeaders()), context.getRequestBody(), profile.timeoutMs,
                profile
            ));
    }

    public Response imageGenerations(String instanceSlug, Object requestBody, jakarta.ws.rs.core.HttpHeaders incomingHeaders) {
        return proxyRequest(instanceSlug, requestBody, "POST", "/v1/images/generations", incomingHeaders,
            (profile, context) -> upstreamHttpClient.send(
                "POST", profile.baseUrl, "/images/generations",
                toMultiMap(context.getRequestHeaders()), context.getRequestBody(), profile.timeoutMs,
                profile
            ));
    }

    @FunctionalInterface
    private interface UpstreamCall {
        UpstreamHttpClient.UpstreamResponse execute(ProxyProfile profile, PluginContext context);
    }

    private Response proxyRequest(String instanceSlug, Object requestBody,
                                   String method, String requestPath,
                                   jakarta.ws.rs.core.HttpHeaders incomingHeaders,
                                   UpstreamCall upstreamCall) {
        ProxyContext ctx = resolveInstance(instanceSlug);
        List<Plugin> plugins = loadPlugins(ctx.group);

        PluginContext context = createRequestContext(ctx.profile, requestBody, incomingHeaders);

        long start = System.currentTimeMillis();
        TrafficLog trafficLog = trafficLogService.recordRequest(
            ctx.instance.id, ctx.instance.slug, ctx.profile.id, requestPath, method,
            context.getRequestHeaders(), requestBody,
            ctx.profile.baseUrl, ctx.profile.timeoutMs, false
        );

        pluginExecutionService.executeRequestPlugins(plugins, context);

        if (context.isReturned()) {
            trafficLogService.recordResponse(trafficLog, context.getResponseStatus(),
                context.getResponseHeaders(), context.getResponseBody(),
                System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots());
            return Response.status(context.getResponseStatus())
                .entity(context.getResponseBody())
                .build();
        }

        boolean responseRecorded = false;
        try {
            UpstreamHttpClient.UpstreamResponse upstream = upstreamCall.execute(ctx.profile, context);

            context.setResponseStatus(upstream.statusCode);
            context.getResponseHeaders().putAll(toMap(upstream.headers));
            context.setResponseBody(parseJson(upstream.body));

            pluginExecutionService.executeResponsePlugins(plugins, context);

            circuitBreaker.recordSuccess(ctx.profile.baseUrl);
            trafficLogService.recordResponse(trafficLog, context.getResponseStatus(),
                context.getResponseHeaders(), context.getResponseBody(),
                System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots());
            responseRecorded = true;

            return Response.status(context.getResponseStatus())
                .entity(context.getResponseBody())
                .build();
        } catch (Exception e) {
            int status = (e instanceof jakarta.ws.rs.WebApplicationException wae) ? wae.getResponse().getStatus() : 502;
            trafficLogService.recordResponse(trafficLog, status,
                Map.of(), "Upstream request failed: " + e.getMessage(),
                System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots(), e.getMessage());
            responseRecorded = true;
            throw new WebApplicationException(e.getMessage(), e, Response.status(status).build());
        } finally {
            if (!responseRecorded) {
                trafficLogService.recordResponse(trafficLog, 504,
                    Map.of(), "Upstream request completed without producing a response",
                    System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots(),
                    "Upstream request completed without producing a response");
            }
        }
    }

    public Multi<String> chatCompletionsStream(String instanceSlug, Object requestBody, jakarta.ws.rs.core.HttpHeaders incomingHeaders) {
        ProxyContext ctx = resolveInstance(instanceSlug);
        List<Plugin> plugins = loadPlugins(ctx.group);

        PluginContext context = createRequestContext(ctx.profile, requestBody, incomingHeaders);

        long start = System.currentTimeMillis();
        TrafficLog trafficLog = trafficLogService.recordRequest(
            ctx.instance.id, ctx.instance.slug, ctx.profile.id, "/v1/chat/completions", "POST",
            context.getRequestHeaders(), requestBody,
            ctx.profile.baseUrl, ctx.profile.timeoutMs, true
        );

        pluginExecutionService.executeRequestPlugins(plugins, context);

        if (context.isReturned()) {
            trafficLogService.recordResponse(trafficLog, context.getResponseStatus(),
                context.getResponseHeaders(), context.getResponseBody(),
                System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots());
            String returned = toJson(context.getResponseBody());
            return Multi.createFrom().item("data: " + returned + "\n\ndata: [DONE]\n\n");
        }

        return doStreamChatCompletions(ctx, context, plugins, trafficLog, start);
    }

    private Multi<String> doStreamChatCompletions(ProxyContext ctx, PluginContext context,
                                           List<Plugin> plugins, TrafficLog trafficLog, long start) {
        Future<HttpClientResponse> upstreamFuture;
        try {
            if (!circuitBreaker.allowRequest(ctx.profile.baseUrl)) {
                throw new WebApplicationException("Circuit breaker open for upstream: " + ctx.profile.baseUrl,
                    Response.Status.SERVICE_UNAVAILABLE);
            }
            upstreamFuture = upstreamHttpClient.sendStream(
                "POST", ctx.profile.baseUrl, "/chat/completions",
                toMultiMap(context.getRequestHeaders()), context.getRequestBody(), ctx.profile.timeoutMs
            );
        } catch (Exception e) {
            String msg = e.getMessage();
            int status = e instanceof WebApplicationException wae ? wae.getResponse().getStatus() : 502;
            trafficLogService.recordResponse(trafficLog, status,
                Map.of(), msg, System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots(), msg);
            return Multi.createFrom().emitter(emitter -> {
                Log.error("Streaming upstream failed", e);
                emitter.emit(sseError(msg, status));
                emitter.complete();
            });
        }

        return Multi.createFrom().emitter(emitter -> {
            StringBuilder responseBuffer = new StringBuilder();
            StringBuilder sseBuffer = new StringBuilder();
            List<Map<String, Object>> accumulatedChoices = new ArrayList<>();
            AtomicBoolean logged = new AtomicBoolean(false);

            Runnable recordOnce = () -> {
                if (logged.compareAndSet(false, true)) {
                    int status = context.getResponseStatus() != 0 ? context.getResponseStatus() : 200;
                    executorService.execute(() -> {
                        try {
                            trafficLogService.recordResponse(trafficLog, status,
                                context.getResponseHeaders(), context.getResponseBody(),
                                System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots());
                        } catch (Exception e) {
                            Log.errorf(e, "Failed to record streaming response for log #%d", trafficLog.id);
                        }
                    });
                }
            };

            java.util.function.BiConsumer<Integer, String> recordError = (status, message) -> {
                if (logged.compareAndSet(false, true)) {
                    executorService.execute(() -> {
                        try {
                            trafficLogService.recordResponse(trafficLog, status,
                                Map.of(), message, System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots(), message);
                        } catch (Exception e) {
                            Log.errorf(e, "Failed to record streaming error for log #%d", trafficLog.id);
                        }
                    });
                }
            };

            java.util.concurrent.atomic.AtomicLong lastActivity = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
            java.util.concurrent.ScheduledFuture<?>[] heartbeatHolder = new java.util.concurrent.ScheduledFuture<?>[1];
            Runnable heartbeat = () -> {
                long idle = System.currentTimeMillis() - lastActivity.get();
                Log.debugf("Streaming heartbeat for log #%d: idle %d ms", Long.valueOf(trafficLog.id), Long.valueOf(idle));
                if (idle > 30_000) {
                    String msg = "Upstream idle for " + (idle / 1000) + " seconds, still waiting";
                    executorService.execute(() -> {
                        try {
                            trafficLogService.appendPluginLog(trafficLog, msg);
                        } catch (Exception e) {
                            Log.errorf(e, "Failed to append plugin log for log #%d", trafficLog.id);
                        }
                    });
                }
            };
            heartbeatHolder[0] = heartbeatScheduler.scheduleAtFixedRate(heartbeat, 30, 30, TimeUnit.SECONDS);

            emitter.onTermination(() -> {
                if (heartbeatHolder[0] != null) {
                    heartbeatHolder[0].cancel(false);
                }
            });

            Runnable finishWithError = () -> {
                if (heartbeatHolder[0] != null) {
                    heartbeatHolder[0].cancel(false);
                }
            };

            upstreamFuture.onSuccess(response -> {
                context.setResponseStatus(response.statusCode());
                context.getResponseHeaders().putAll(toMap(response.headers()));
                if (response.statusCode() >= 400) {
                    StringBuilder errorBody = new StringBuilder();
                    response.handler(buffer -> errorBody.append(buffer.toString()));
                    response.exceptionHandler(err -> {
                        finishWithError.run();
                        Log.error("Streaming upstream response failed", err);
                        circuitBreaker.recordFailure(ctx.profile.baseUrl, ctx.profile);
                        String msg = "Upstream response error: " + err.getMessage();
                        recordError.accept(response.statusCode(), msg);
                        emitter.emit(sseError(msg, null));
                        emitter.complete();
                    });
                    response.endHandler(v -> {
                        finishWithError.run();
                        String body = errorBody.toString();
                        context.setResponseBody(parseJson(body));
                        circuitBreaker.recordFailure(ctx.profile.baseUrl, ctx.profile);
                        String msg = "Upstream returned " + response.statusCode()
                            + (body.isBlank() ? "" : ": " + body);
                        recordError.accept(response.statusCode(), msg);
                        emitter.emit(sseError(msg, response.statusCode()));
                        emitter.complete();
                    });
                    return;
                }
                response.handler(buffer -> {
                    lastActivity.set(System.currentTimeMillis());
                    String chunk = buffer.toString();
                    responseBuffer.append(chunk);
                    sseBuffer.append(chunk);
                    String[] lines = sseBuffer.toString().split("\n", -1);
                    sseBuffer.setLength(0);
                    sseBuffer.append(lines[lines.length - 1]);
                    for (int i = 0; i < lines.length - 1; i++) {
                        String line = lines[i].trim();
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) {
                                Object parsedBody = buildStreamingResponseBody(accumulatedChoices);
                                context.setResponseBody(parsedBody);
                                pluginExecutionService.executeResponsePlugins(plugins, context);
                                circuitBreaker.recordSuccess(ctx.profile.baseUrl);
                                recordOnce.run();
                                emitter.complete();
                                return;
                            }
                            emitter.emit("data: " + data + "\n\n");
                            accumulateStreamDelta(data, accumulatedChoices);
                        }
                    }
                });
                response.endHandler(v -> {
                    finishWithError.run();
                    String remaining = sseBuffer.toString().trim();
                    if (remaining.startsWith("data: ")) {
                        String data = remaining.substring(6).trim();
                        if (!"[DONE]".equals(data)) {
                            emitter.emit("data: " + data + "\n\n");
                            accumulateStreamDelta(data, accumulatedChoices);
                        }
                    }
                    Object parsedBody = buildStreamingResponseBody(accumulatedChoices);
                    context.setResponseBody(parsedBody);
                    pluginExecutionService.executeResponsePlugins(plugins, context);
                    circuitBreaker.recordSuccess(ctx.profile.baseUrl);
                    recordOnce.run();
                    emitter.complete();
                });
            }).onFailure(err -> {
                finishWithError.run();
                Log.error("Streaming upstream failed", err);
                circuitBreaker.recordFailure(ctx.profile.baseUrl, ctx.profile);
                String msg = err.getMessage();
                recordError.accept(502, msg);
                emitter.emit(sseError(msg, null));
                emitter.complete();
            });
        });
    }

    private Object buildStreamingResponseBody(List<Map<String, Object>> accumulatedChoices) {
        if (accumulatedChoices.isEmpty()) {
            return Map.of("choices", List.of(Map.of("delta", Map.of())));
        }
        return Map.of("choices", (Object) accumulatedChoices);
    }

    private void accumulateStreamDelta(String data, List<Map<String, Object>> accumulatedChoices) {
        try {
            JsonNode node = objectMapper.readTree(data);
            JsonNode choices = node.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) return;
            for (int i = 0; i < choices.size(); i++) {
                JsonNode choice = choices.get(i);
                JsonNode delta = choice.get("delta");
                if (delta == null) continue;
                while (accumulatedChoices.size() <= i) {
                    accumulatedChoices.add(new LinkedHashMap<>());
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> accChoice = accumulatedChoices.get(i);
                @SuppressWarnings("unchecked")
                Map<String, Object> accDelta = (Map<String, Object>) accChoice.computeIfAbsent("delta", k -> new LinkedHashMap<>());
                if (!accChoice.containsKey("index")) {
                    accChoice.put("index", i);
                }
                JsonNode finishReason = choice.get("finish_reason");
                if (finishReason != null && !finishReason.isNull()) {
                    accChoice.put("finish_reason", finishReason.asText());
                }
                mergeDeltaFields(delta, accDelta);
            }
        } catch (IOException e) {
            Log.debugf("Ignoring malformed SSE chunk: %s", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeDeltaFields(JsonNode delta, Map<String, Object> accDelta) {
        var fields = delta.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();
            if ("tool_calls".equals(key) && value.isArray()) {
                List<Map<String, Object>> accToolCalls = (List<Map<String, Object>>) accDelta.computeIfAbsent("tool_calls", k -> new ArrayList<>());
                for (JsonNode tc : value) {
                    int tcIndex = tc.has("index") ? tc.get("index").asInt() : 0;
                    while (accToolCalls.size() <= tcIndex) {
                        accToolCalls.add(new LinkedHashMap<>());
                    }
                    Map<String, Object> accTc = accToolCalls.get(tcIndex);
                    if (tc.has("id")) accTc.putIfAbsent("id", tc.get("id").asText());
                    if (tc.has("type")) accTc.put("type", tc.get("type").asText());
                    if (!accTc.containsKey("index")) accTc.put("index", tcIndex);
                    if (tc.has("function")) {
                        Map<String, Object> accFn = (Map<String, Object>) accTc.computeIfAbsent("function", k -> new LinkedHashMap<>());
                        JsonNode fn = tc.get("function");
                        if (fn.has("name")) accFn.putIfAbsent("name", fn.get("name").asText());
                        if (fn.has("arguments")) {
                            accFn.merge("arguments", fn.get("arguments").asText(), (a, b) -> (String) a + (String) b);
                        }
                    }
                }
            } else if ("role".equals(key)) {
                accDelta.putIfAbsent("role", value.asText());
            } else if (value.isTextual()) {
                accDelta.merge(key, value.asText(), (a, b) -> (String) a + (String) b);
            } else {
                accDelta.putIfAbsent(key, objectMapper.convertValue(value, Object.class));
            }
        }
    }

    private ProxyContext resolveInstance(String instanceSlug) {
        Optional<ProxyInstance> instanceOpt = instanceRepository.findBySlug(instanceSlug);
        if (instanceOpt.isEmpty()) {
            throw new WebApplicationException("Instance not found: " + instanceSlug, Response.Status.NOT_FOUND);
        }
        ProxyInstance instance = instanceOpt.get();
        if (!instance.enabled) {
            throw new WebApplicationException("Instance disabled: " + instanceSlug, Response.Status.FORBIDDEN);
        }

        ProxyProfile profile = profileRepository.findById(instance.profileId)
            .orElseThrow(() -> new WebApplicationException("Upstream profile not found for instance: " + instanceSlug, Response.Status.NOT_FOUND));
        if (!profile.enabled) {
            throw new WebApplicationException("Upstream profile disabled for instance: " + instanceSlug, Response.Status.FORBIDDEN);
        }

        PluginGroup group = pluginGroupRepository.findById(instance.pluginGroupId)
            .orElseThrow(() -> new WebApplicationException("Plugin group not found for instance: " + instanceSlug, Response.Status.NOT_FOUND));

        return new ProxyContext(instance, profile, group);
    }

    private record ProxyContext(ProxyInstance instance, ProxyProfile profile, PluginGroup group) {
    }

    private List<Plugin> loadPlugins(PluginGroup group) {
        List<Plugin> plugins = new ArrayList<>();
        if (group == null || group.items == null) {
            return plugins;
        }
        for (PluginGroupItem item : group.sortedItems()) {
            if (item.enabled && item.plugin != null && item.plugin.enabled) {
                plugins.add(item.plugin);
            }
        }
        return plugins;
    }

    private PluginContext createRequestContext(ProxyProfile profile, Object requestBody, jakarta.ws.rs.core.HttpHeaders incomingHeaders) {
        PluginContext context = new PluginContext();
        // Priority (low -> high): client request body -> profile custom body -> plugins
        context.setRequestBody(mergeRequestBody(requestBody, profile));
        // Priority (low -> high): client headers -> profile custom headers -> bearer token -> plugins
        if (incomingHeaders != null) {
            incomingHeaders.getRequestHeaders().forEach((key, values) -> {
                if (values != null && !values.isEmpty() && !isHopByHopHeader(key)) {
                    context.getRequestHeaders().put(key, values.get(0));
                }
            });
        }
        addCustomHeaders(context, profile);
        if (profile.bearerToken != null && !profile.bearerToken.isBlank()) {
            context.getRequestHeaders().put("Authorization", "Bearer " + profile.bearerToken);
        }
        return context;
    }

    private void addCustomHeaders(PluginContext context, ProxyProfile profile) {
        if (profile.customHeaders == null || profile.customHeaders.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(profile.customHeaders);
            if (node.isObject()) {
                node.fields().forEachRemaining(entry ->
                    context.getRequestHeaders().put(entry.getKey(), entry.getValue().asText())
                );
            }
        } catch (IOException e) {
            Log.warnf("Failed to parse custom headers for profile %s: %s", profile.slug, e.getMessage());
        }
    }

    private Object mergeRequestBody(Object requestBody, ProxyProfile profile) {
        ObjectNode base = objectMapper.createObjectNode();
        if (requestBody != null) {
            JsonNode incoming = objectMapper.valueToTree(requestBody);
            if (incoming.isObject()) {
                base.setAll((ObjectNode) incoming);
            } else {
                return requestBody;
            }
        }
        if (profile.customBody != null && !profile.customBody.isBlank()) {
            try {
                JsonNode custom = objectMapper.readTree(profile.customBody);
                if (custom.isObject()) {
                    base.setAll((ObjectNode) custom);
                }
            } catch (IOException e) {
                Log.warnf("Failed to parse custom body for profile %s: %s", profile.slug, e.getMessage());
            }
        }
        // Convert back to plain Map/List so Groovy/JS plugins can mutate in-place
        return objectMapper.convertValue(base, new TypeReference<Map<String, Object>>() {});
    }

    private boolean isHopByHopHeader(String name) {
        if (name == null) return true;
        String lower = name.toLowerCase();
        return lower.equals("host")
            || lower.equals("connection")
            || lower.equals("keep-alive")
            || lower.equals("proxy-authenticate")
            || lower.equals("proxy-authorization")
            || lower.equals("te")
            || lower.equals("trailer")
            || lower.equals("transfer-encoding")
            || lower.equals("content-encoding")
            || lower.equals("accept-encoding")
            || lower.equals("upgrade")
            || lower.equals("content-length");
    }

    private MultiMap toMultiMap(java.util.Map<String, String> headers) {
        MultiMap result = MultiMap.caseInsensitiveMultiMap();
        headers.forEach(result::add);
        return result;
    }

    private java.util.Map<String, String> toMap(MultiMap headers) {
        java.util.Map<String, String> result = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private Object parseJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (IOException e) {
            return body;
        }
    }

    private String toJson(Object body) {
        if (body == null) {
            return "{}";
        }
        try {
            return (body instanceof String) ? (String) body : objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new WebApplicationException("Failed to serialize request body", e);
        }
    }

    private String sseError(String message, Integer statusCode) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", message);
        if (statusCode != null) {
            error.put("code", statusCode);
        }
        return "event: error\ndata: " + toJson(Map.of("error", error)) + "\n\n";
    }
}
