package com.xenoamess.damning_proxy.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public Response listModels(String instanceSlug) {
        ProxyContext ctx = resolveInstance(instanceSlug);
        List<Plugin> plugins = loadPlugins(ctx.group);

        long start = System.currentTimeMillis();
        TrafficLog trafficLog = trafficLogService.recordRequest(
            ctx.instance.id, ctx.profile.id, "/v1/models", "GET", Map.of(), null
        );

        PluginContext context = createRequestContext(ctx.profile, null);
        pluginExecutionService.executeRequestPlugins(plugins, context);

        if (context.isReturned()) {
            trafficLogService.recordResponse(trafficLog, context.getResponseStatus(),
                context.getResponseHeaders(), context.getResponseBody(),
                System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots());
            return Response.status(context.getResponseStatus())
                .entity(context.getResponseBody())
                .build();
        }

        UpstreamHttpClient.UpstreamResponse upstream = upstreamHttpClient.send(
            "GET", ctx.profile.baseUrl, "/models",
            toMultiMap(context.getRequestHeaders()), null, ctx.profile.timeoutMs
        );

        context.setResponseStatus(upstream.statusCode);
        context.setResponseBody(parseJson(upstream.body));

        pluginExecutionService.executeResponsePlugins(plugins, context);

        trafficLogService.recordResponse(trafficLog, context.getResponseStatus(),
            context.getResponseHeaders(), context.getResponseBody(),
            System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots());

        return Response.status(context.getResponseStatus())
            .entity(context.getResponseBody())
            .build();
    }

    public Response chatCompletions(String instanceSlug, Object requestBody) {
        ProxyContext ctx = resolveInstance(instanceSlug);
        List<Plugin> plugins = loadPlugins(ctx.group);

        long start = System.currentTimeMillis();
        Map<String, String> initialHeaders = new HashMap<>();
        TrafficLog trafficLog = trafficLogService.recordRequest(
            ctx.instance.id, ctx.profile.id, "/v1/chat/completions", "POST", initialHeaders, requestBody
        );

        PluginContext context = createRequestContext(ctx.profile, requestBody);
        pluginExecutionService.executeRequestPlugins(plugins, context);

        if (context.isReturned()) {
            trafficLogService.recordResponse(trafficLog, context.getResponseStatus(),
                context.getResponseHeaders(), context.getResponseBody(),
                System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots());
            return Response.status(context.getResponseStatus())
                .entity(context.getResponseBody())
                .build();
        }

        boolean streaming = isStreamingRequest(context.getRequestBody());

        if (streaming) {
            throw new jakarta.ws.rs.WebApplicationException("Streaming requests are not supported by this endpoint, use /chat/completions with Accept: text/event-stream", jakarta.ws.rs.core.Response.Status.BAD_REQUEST);
        }

        UpstreamHttpClient.UpstreamResponse upstream = upstreamHttpClient.send(
            "POST", ctx.profile.baseUrl, "/chat/completions",
            toMultiMap(context.getRequestHeaders()), context.getRequestBody(), ctx.profile.timeoutMs
        );

        context.setResponseStatus(upstream.statusCode);
        context.setResponseBody(parseJson(upstream.body));

        pluginExecutionService.executeResponsePlugins(plugins, context);

        trafficLogService.recordResponse(trafficLog, context.getResponseStatus(),
            context.getResponseHeaders(), context.getResponseBody(),
            System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots());

        return Response.status(context.getResponseStatus())
            .entity(context.getResponseBody())
            .build();
    }

    public Multi<String> chatCompletionsStream(String instanceSlug, Object requestBody) {
        ProxyContext ctx = resolveInstance(instanceSlug);
        List<Plugin> plugins = loadPlugins(ctx.group);

        long start = System.currentTimeMillis();
        Map<String, String> initialHeaders = new HashMap<>();
        TrafficLog trafficLog = trafficLogService.recordRequest(
            ctx.instance.id, ctx.profile.id, "/v1/chat/completions", "POST", initialHeaders, requestBody
        );

        PluginContext context = createRequestContext(ctx.profile, requestBody);
        pluginExecutionService.executeRequestPlugins(plugins, context);

        if (context.isReturned()) {
            trafficLogService.recordResponse(trafficLog, context.getResponseStatus(),
                context.getResponseHeaders(), context.getResponseBody(),
                System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots());
            String returned = toJson(context.getResponseBody());
            return Multi.createFrom().item("data: " + returned + "\n\ndata: [DONE]\n\n");
        }

        return streamChatCompletions(ctx, context, plugins, trafficLog, start);
    }

    private Multi<String> streamChatCompletions(ProxyContext ctx, PluginContext context,
                                           List<Plugin> plugins, TrafficLog trafficLog, long start) {
        Future<HttpClientResponse> upstreamFuture = upstreamHttpClient.sendStream(
            "POST", ctx.profile.baseUrl, "/chat/completions",
            toMultiMap(context.getRequestHeaders()), context.getRequestBody(), ctx.profile.timeoutMs
        );

        return Multi.createFrom().emitter(emitter -> {
            StringBuilder responseBuffer = new StringBuilder();
            StringBuilder sseBuffer = new StringBuilder();
            upstreamFuture.onSuccess(response -> {
                response.handler(buffer -> {
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
                                emitter.complete();
                                return;
                            }
                            emitter.emit(data);
                        }
                    }
                });
                response.endHandler(v -> {
                    String remaining = sseBuffer.toString().trim();
                    if (remaining.startsWith("data: ")) {
                        String data = remaining.substring(6).trim();
                        if (!"[DONE]".equals(data)) {
                            emitter.emit(data);
                        }
                    }
                    context.setResponseBody(responseBuffer.toString());
                    pluginExecutionService.executeResponsePlugins(plugins, context);
                    executorService.execute(() -> trafficLogService.recordResponse(trafficLog, 200,
                        context.getResponseHeaders(), context.getResponseBody(),
                        System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots()));
                    emitter.complete();
                });
            }).onFailure(err -> {
                Log.error("Streaming upstream failed", err);
                executorService.execute(() -> trafficLogService.recordResponse(trafficLog, 502,
                    Map.of(), err.getMessage(), System.currentTimeMillis() - start, context.getPluginLogs(), context.getFriendlyLogCollector().getSnapshots()));
                emitter.fail(err);
            });
        });
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

    private PluginContext createRequestContext(ProxyProfile profile, Object requestBody) {
        PluginContext context = new PluginContext();
        context.setRequestBody(requestBody);
        if (profile.bearerToken != null && !profile.bearerToken.isBlank()) {
            context.getRequestHeaders().put("Authorization", "Bearer " + profile.bearerToken);
        }
        addCustomHeaders(context, profile);
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

    private MultiMap toMultiMap(java.util.Map<String, String> headers) {
        MultiMap result = MultiMap.caseInsensitiveMultiMap();
        headers.forEach(result::add);
        return result;
    }

    private boolean isStreamingRequest(Object requestBody) {
        if (requestBody == null) {
            return false;
        }
        try {
            JsonNode node = objectMapper.valueToTree(requestBody);
            return node.has("stream") && node.get("stream").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
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

    @SuppressWarnings("unused")
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
