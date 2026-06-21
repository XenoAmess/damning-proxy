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
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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

    public Response listModels(String instanceSlug, jakarta.ws.rs.core.HttpHeaders incomingHeaders) {
        ProxyContext ctx = resolveInstance(instanceSlug);
        List<Plugin> plugins = loadPlugins(ctx.group);

        PluginContext context = createRequestContext(ctx.profile, null, incomingHeaders);

        long start = System.currentTimeMillis();
        TrafficLog trafficLog = trafficLogService.recordRequest(
            ctx.instance.id, ctx.instance.slug, ctx.profile.id, "/v1/models", "GET",
            context.getRequestHeaders(), null,
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
            UpstreamHttpClient.UpstreamResponse upstream = upstreamHttpClient.send(
                "GET", ctx.profile.baseUrl, "/models",
                toMultiMap(context.getRequestHeaders()), null, ctx.profile.timeoutMs
            );

            context.setResponseStatus(upstream.statusCode);
            context.getResponseHeaders().putAll(toMap(upstream.headers));
            context.setResponseBody(parseJson(upstream.body));

            pluginExecutionService.executeResponsePlugins(plugins, context);

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
            throw e;
        } finally {
            if (!responseRecorded) {
                trafficLogService.recordResponse(trafficLog, 504,
                    Map.of(), "Upstream request completed without producing a response",
                    System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots(),
                    "Upstream request completed without producing a response");
            }
        }
    }

    public Response chatCompletions(String instanceSlug, Object requestBody, jakarta.ws.rs.core.HttpHeaders incomingHeaders) {
        ProxyContext ctx = resolveInstance(instanceSlug);
        List<Plugin> plugins = loadPlugins(ctx.group);

        PluginContext context = createRequestContext(ctx.profile, requestBody, incomingHeaders);

        long start = System.currentTimeMillis();
        TrafficLog trafficLog = trafficLogService.recordRequest(
            ctx.instance.id, ctx.instance.slug, ctx.profile.id, "/v1/chat/completions", "POST",
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
            UpstreamHttpClient.UpstreamResponse upstream = upstreamHttpClient.send(
                "POST", ctx.profile.baseUrl, "/chat/completions",
                toMultiMap(context.getRequestHeaders()), context.getRequestBody(), ctx.profile.timeoutMs
            );

            context.setResponseStatus(upstream.statusCode);
            context.getResponseHeaders().putAll(toMap(upstream.headers));
            context.setResponseBody(parseJson(upstream.body));

            pluginExecutionService.executeResponsePlugins(plugins, context);

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
            throw e;
        } finally {
            // Safety net: if neither the success path nor the catch block recorded
            // a response (e.g. the upstream send() returned silently without
            // throwing and we somehow skipped recordResponse), still capture
            // whatever plugin state was produced so traffic logs reflect the
            // request-phase plugin activity.
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
        Future<HttpClientResponse> upstreamFuture = upstreamHttpClient.sendStream(
            "POST", ctx.profile.baseUrl, "/chat/completions",
            toMultiMap(context.getRequestHeaders()), context.getRequestBody(), ctx.profile.timeoutMs
        );

        return Multi.createFrom().emitter(emitter -> {
            StringBuilder responseBuffer = new StringBuilder();
            StringBuilder sseBuffer = new StringBuilder();
            StringBuilder contentBuffer = new StringBuilder();
            AtomicBoolean logged = new AtomicBoolean(false);

            Runnable recordOnce = () -> {
                if (logged.compareAndSet(false, true)) {
                    int status = context.getResponseStatus() != 0 ? context.getResponseStatus() : 200;
                    executorService.execute(() -> trafficLogService.recordResponse(trafficLog, status,
                        context.getResponseHeaders(), context.getResponseBody(),
                        System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots()));
                }
            };

            java.util.function.Consumer<String> recordError = (message) -> {
                if (logged.compareAndSet(false, true)) {
                    executorService.execute(() -> trafficLogService.recordResponse(trafficLog, 502,
                        Map.of(), message, System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots(), message));
                }
            };

            java.util.concurrent.atomic.AtomicLong lastActivity = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
            java.util.concurrent.ScheduledFuture<?>[] heartbeatHolder = new java.util.concurrent.ScheduledFuture<?>[1];
            Runnable heartbeat = () -> {
                long idle = System.currentTimeMillis() - lastActivity.get();
                Log.debugf("Streaming heartbeat for log #%d: idle %d ms", Long.valueOf(trafficLog.id), Long.valueOf(idle));
                if (idle > 30_000) {
                    String msg = "Upstream idle for " + (idle / 1000) + " seconds, still waiting";
                    executorService.execute(() -> trafficLogService.appendPluginLog(trafficLog, msg));
                }
            };
            heartbeatHolder[0] = heartbeatScheduler.scheduleAtFixedRate(heartbeat, 30, 30, TimeUnit.SECONDS);

            emitter.onTermination(() -> {
                if (heartbeatHolder[0] != null) {
                    heartbeatHolder[0].cancel(false);
                }
                if (logged.compareAndSet(false, true)) {
                    String message = "Client closed connection or stream terminated";
                    Log.warnf("Streaming request terminated (client cancelled or failure) for log #%d", trafficLog.id);
                    executorService.execute(() -> trafficLogService.recordResponse(trafficLog, 499,
                        Map.of(), message, System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots(), message));
                }
            });

            upstreamFuture.onSuccess(response -> {
                context.setResponseStatus(response.statusCode());
                context.getResponseHeaders().putAll(toMap(response.headers()));
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
                                recordOnce.run();
                                emitter.complete();
                                return;
                            }
                            emitter.emit("data: " + data + "\n\n");
                            accumulateStreamContent(data, contentBuffer);
                        }
                    }
                });
                response.endHandler(v -> {
                    if (heartbeatHolder[0] != null) {
                        heartbeatHolder[0].cancel(false);
                    }
                    String remaining = sseBuffer.toString().trim();
                    if (remaining.startsWith("data: ")) {
                        String data = remaining.substring(6).trim();
                        if (!"[DONE]".equals(data)) {
                            emitter.emit("data: " + data + "\n\n");
                            accumulateStreamContent(data, contentBuffer);
                        }
                    }
                    Object parsedBody = buildStreamingResponseBody(contentBuffer.toString());
                    context.setResponseBody(parsedBody);
                    pluginExecutionService.executeResponsePlugins(plugins, context);
                    recordOnce.run();
                    emitter.complete();
                });
            }).onFailure(err -> {
                if (heartbeatHolder[0] != null) {
                    heartbeatHolder[0].cancel(false);
                }
                Log.error("Streaming upstream failed", err);
                recordError.accept(err.getMessage());
                emitter.fail(err);
            });
        });
    }

    private Object buildStreamingResponseBody(String accumulatedContent) {
        return Map.of("choices", List.of(Map.of("delta", Map.of("content", accumulatedContent == null ? "" : accumulatedContent))));
    }

    private void accumulateStreamContent(String data, StringBuilder buffer) {
        try {
            JsonNode node = objectMapper.readTree(data);
            JsonNode choices = node.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) return;
            JsonNode delta = choices.get(0).get("delta");
            if (delta == null) return;
            JsonNode reasoning = delta.get("reasoning_content");
            JsonNode content = delta.get("content");
            if (reasoning != null && !reasoning.isNull()) {
                buffer.append(reasoning.asText());
            }
            if (content != null && !content.isNull()) {
                buffer.append(content.asText());
            }
        } catch (IOException e) {
            // ignore malformed chunk
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
        java.util.Map<String, String> result = new HashMap<>();
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
}
