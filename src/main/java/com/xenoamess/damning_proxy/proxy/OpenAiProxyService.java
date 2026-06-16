package com.xenoamess.damning_proxy.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.entity.TrafficLog;
import com.xenoamess.damning_proxy.plugin.PluginContext;
import com.xenoamess.damning_proxy.plugin.PluginExecutionService;
import com.xenoamess.damning_proxy.repository.PluginRepository;
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

@ApplicationScoped
public class OpenAiProxyService {

    @Inject
    ProfileRepository profileRepository;

    @Inject
    PluginRepository pluginRepository;

    @Inject
    PluginExecutionService pluginExecutionService;

    @Inject
    UpstreamHttpClient upstreamHttpClient;

    @Inject
    TrafficLogService trafficLogService;

    @Inject
    ObjectMapper objectMapper;

    public Response listModels(String profileSlug) {
        ProxyProfile profile = findEnabledProfile(profileSlug);
        List<Plugin> plugins = loadPlugins(profile);

        long start = System.currentTimeMillis();
        TrafficLog trafficLog = trafficLogService.recordRequest(
            profile.id, "/v1/models", "GET", Map.of(), null
        );

        PluginContext context = createRequestContext(profile, null);
        pluginExecutionService.executeRequestPlugins(plugins, context);

        if (context.isReturned()) {
            trafficLogService.recordResponse(trafficLog, context.getResponseStatus(),
                context.getResponseHeaders(), context.getResponseBody(),
                System.currentTimeMillis() - start, context.getPluginLogs());
            return Response.status(context.getResponseStatus())
                .entity(context.getResponseBody())
                .build();
        }

        UpstreamHttpClient.UpstreamResponse upstream = upstreamHttpClient.send(
            "GET", profile.baseUrl, "/models",
            toMultiMap(context.getRequestHeaders()), null, profile.timeoutMs
        );

        context.setResponseStatus(upstream.statusCode);
        context.setResponseBody(parseJson(upstream.body));

        pluginExecutionService.executeResponsePlugins(plugins, context);

        trafficLogService.recordResponse(trafficLog, context.getResponseStatus(),
            context.getResponseHeaders(), context.getResponseBody(),
            System.currentTimeMillis() - start, context.getPluginLogs());

        return Response.status(context.getResponseStatus())
            .entity(context.getResponseBody())
            .build();
    }

    public Response chatCompletions(String profileSlug, Object requestBody) {
        ProxyProfile profile = findEnabledProfile(profileSlug);
        List<Plugin> plugins = loadPlugins(profile);

        long start = System.currentTimeMillis();
        Map<String, String> initialHeaders = new HashMap<>();
        TrafficLog trafficLog = trafficLogService.recordRequest(
            profile.id, "/v1/chat/completions", "POST", initialHeaders, requestBody
        );

        PluginContext context = createRequestContext(profile, requestBody);
        pluginExecutionService.executeRequestPlugins(plugins, context);

        if (context.isReturned()) {
            trafficLogService.recordResponse(trafficLog, context.getResponseStatus(),
                context.getResponseHeaders(), context.getResponseBody(),
                System.currentTimeMillis() - start, context.getPluginLogs());
            return Response.status(context.getResponseStatus())
                .entity(context.getResponseBody())
                .build();
        }

        boolean streaming = isStreamingRequest(context.getRequestBody());

        if (streaming) {
            return streamChatCompletions(profile, context, plugins, trafficLog, start);
        } else {
            UpstreamHttpClient.UpstreamResponse upstream = upstreamHttpClient.send(
                "POST", profile.baseUrl, "/chat/completions",
                toMultiMap(context.getRequestHeaders()), context.getRequestBody(), profile.timeoutMs
            );

            context.setResponseStatus(upstream.statusCode);
            context.setResponseBody(parseJson(upstream.body));

            pluginExecutionService.executeResponsePlugins(plugins, context);

            trafficLogService.recordResponse(trafficLog, context.getResponseStatus(),
                context.getResponseHeaders(), context.getResponseBody(),
                System.currentTimeMillis() - start, context.getPluginLogs());

            return Response.status(context.getResponseStatus())
                .entity(context.getResponseBody())
                .build();
        }
    }

    private Response streamChatCompletions(ProxyProfile profile, PluginContext context,
                                           List<Plugin> plugins, TrafficLog trafficLog, long start) {
        String requestJson = toJson(context.getRequestBody());

        Future<HttpClientResponse> upstreamFuture = upstreamHttpClient.sendStream(
            "POST", profile.baseUrl, "/chat/completions",
            toMultiMap(context.getRequestHeaders()), context.getRequestBody(), profile.timeoutMs
        );

        Multi<String> stream = Multi.createFrom().emitter(emitter -> {
            StringBuilder responseBuffer = new StringBuilder();
            upstreamFuture.onSuccess(response -> {
                response.handler(buffer -> {
                    String chunk = buffer.toString();
                    responseBuffer.append(chunk);
                    emitter.emit(chunk);
                });
                response.endHandler(v -> {
                    context.setResponseBody(responseBuffer.toString());
                    pluginExecutionService.executeResponsePlugins(plugins, context);
                    trafficLogService.recordResponse(trafficLog, 200,
                        context.getResponseHeaders(), context.getResponseBody(),
                        System.currentTimeMillis() - start, context.getPluginLogs());
                    emitter.complete();
                });
            }).onFailure(err -> {
                Log.error("Streaming upstream failed", err);
                trafficLogService.recordResponse(trafficLog, 502,
                    Map.of(), err.getMessage(), System.currentTimeMillis() - start, context.getPluginLogs());
                emitter.fail(err);
            });
        });

        return Response.ok(stream)
            .header(HttpHeaders.CONTENT_TYPE.toString(), MediaType.SERVER_SENT_EVENTS)
            .build();
    }

    private ProxyProfile findEnabledProfile(String profileSlug) {
        Optional<ProxyProfile> profileOpt = profileRepository.findBySlug(profileSlug);
        if (profileOpt.isEmpty()) {
            throw new WebApplicationException("Profile not found: " + profileSlug, Response.Status.NOT_FOUND);
        }
        ProxyProfile profile = profileOpt.get();
        if (!profile.enabled) {
            throw new WebApplicationException("Profile disabled: " + profileSlug, Response.Status.FORBIDDEN);
        }
        return profile;
    }

    private List<Plugin> loadPlugins(ProxyProfile profile) {
        List<Plugin> plugins = new ArrayList<>();
        plugins.addAll(pluginRepository.findEnabledGlobal());
        plugins.addAll(pluginRepository.findEnabledByProfileId(profile.id));
        plugins.sort((a, b) -> Integer.compare(a.priority, b.priority));
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
