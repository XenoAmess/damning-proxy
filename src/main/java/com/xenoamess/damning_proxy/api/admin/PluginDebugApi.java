package com.xenoamess.damning_proxy.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xenoamess.damning_proxy.dto.PluginDryRunRequest;
import com.xenoamess.damning_proxy.dto.PluginDryRunResponse;
import com.xenoamess.damning_proxy.dto.PluginExecutionSnapshot;
import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.entity.ProxyInstance;
import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.plugin.PluginContext;
import com.xenoamess.damning_proxy.plugin.PluginExecutionService;
import com.xenoamess.damning_proxy.repository.InstanceRepository;
import com.xenoamess.damning_proxy.repository.PluginRepository;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/api/plugins/{id}/dry-run")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PluginDebugApi {

    @Inject
    PluginRepository pluginRepository;

    @Inject
    InstanceRepository instanceRepository;

    @Inject
    ProfileRepository profileRepository;

    @Inject
    PluginExecutionService pluginExecutionService;

    @Inject
    ObjectMapper objectMapper;

    @POST
    public Response dryRun(@PathParam("id") Long id, PluginDryRunRequest request) {
        if (request == null || request.phase == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("phase is required").build();
        }
        Plugin plugin = pluginRepository.findById(id).orElse(null);
        if (plugin == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Plugin not found").build();
        }

        PluginContext context = buildContext(request);
        List<Plugin> plugins = List.of(plugin);

        try {
            if (request.phase == Plugin.ExecutionPhase.REQUEST) {
                pluginExecutionService.executeRequestPlugins(plugins, context);
            } else {
                pluginExecutionService.executeResponsePlugins(plugins, context);
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Failed to execute plugin: " + e.getMessage())
                .build();
        }

        PluginDryRunResponse response = new PluginDryRunResponse();
        response.pluginName = plugin.name;
        response.phase = request.phase.name();
        response.pluginLogs = new ArrayList<>(context.getPluginLogs());
        response.requestBody = context.getRequestBody();
        response.requestHeaders = Map.copyOf(context.getRequestHeaders());
        response.responseBody = context.getResponseBody();
        response.responseHeaders = Map.copyOf(context.getResponseHeaders());
        response.responseStatus = context.getResponseStatus();
        response.stopped = context.isStopped();
        response.returned = context.isReturned();

        List<PluginExecutionSnapshot> snapshots = context.getFriendlyLogCollector().getSnapshots();
        if (!snapshots.isEmpty()) {
            PluginExecutionSnapshot snapshot = snapshots.get(0);
            response.input = snapshot.input;
            response.output = snapshot.output;
            response.error = snapshot.error;
            response.errorMessage = snapshot.log;
        } else {
            response.input = request.phase == Plugin.ExecutionPhase.REQUEST ? request.requestBody : request.responseBody;
            response.output = response.input;
        }

        return Response.ok(response).build();
    }

    private PluginContext buildContext(PluginDryRunRequest request) {
        PluginContext context = new PluginContext();

        ProxyProfile profile = null;
        if (request.instanceId != null) {
            ProxyInstance instance = instanceRepository.findById(request.instanceId).orElse(null);
            if (instance != null) {
                profile = profileRepository.findById(instance.profileId).orElse(null);
            }
        }

        Object requestBody = mergeRequestBody(request.requestBody, profile);
        context.setRequestBody(requestBody);

        if (request.requestHeaders != null) {
            context.getRequestHeaders().putAll(request.requestHeaders);
        }
        addCustomHeaders(context, profile);
        if (profile != null && profile.bearerToken != null && !profile.bearerToken.isBlank()) {
            context.getRequestHeaders().put("Authorization", "Bearer " + profile.bearerToken);
        }

        if (request.responseBody != null) {
            context.setResponseBody(deepCopy(request.responseBody));
        }
        if (request.responseHeaders != null) {
            context.getResponseHeaders().putAll(request.responseHeaders);
        }
        if (request.responseStatus != null) {
            context.setResponseStatus(request.responseStatus);
        }

        return context;
    }

    private Object mergeRequestBody(Object requestBody, ProxyProfile profile) {
        if (profile == null || profile.customBody == null || profile.customBody.isBlank()) {
            return deepCopy(requestBody);
        }
        com.fasterxml.jackson.databind.node.ObjectNode base = objectMapper.createObjectNode();
        if (requestBody != null) {
            com.fasterxml.jackson.databind.JsonNode incoming = objectMapper.valueToTree(requestBody);
            if (incoming.isObject()) {
                base.setAll((com.fasterxml.jackson.databind.node.ObjectNode) incoming);
            } else {
                return deepCopy(requestBody);
            }
        }
        try {
            com.fasterxml.jackson.databind.JsonNode custom = objectMapper.readTree(profile.customBody);
            if (custom.isObject()) {
                base.setAll((com.fasterxml.jackson.databind.node.ObjectNode) custom);
            }
        } catch (IOException e) {
            io.quarkus.logging.Log.warnf("Failed to parse custom body for profile %s: %s", profile.slug, e.getMessage());
        }
        return objectMapper.convertValue(base, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    private void addCustomHeaders(PluginContext context, ProxyProfile profile) {
        if (profile == null || profile.customHeaders == null || profile.customHeaders.isBlank()) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(profile.customHeaders);
            if (node.isObject()) {
                node.fields().forEachRemaining(entry ->
                    context.getRequestHeaders().put(entry.getKey(), entry.getValue().asText())
                );
            }
        } catch (IOException e) {
            io.quarkus.logging.Log.warnf("Failed to parse custom headers for profile %s: %s", profile.slug, e.getMessage());
        }
    }

    private Object deepCopy(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(value), Object.class);
        } catch (Exception e) {
            return value;
        }
    }
}
