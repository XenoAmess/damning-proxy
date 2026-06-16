package com.xenoamess.damning_proxy.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xenoamess.damning_proxy.dto.PluginExecutionSnapshot;
import com.xenoamess.damning_proxy.dto.TrafficLogFriendlyDto;
import com.xenoamess.damning_proxy.entity.TrafficLog;
import com.xenoamess.damning_proxy.repository.LogRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Path("/api/logs")
@Produces(MediaType.APPLICATION_JSON)
public class LogAdminApi {

    @Inject
    LogRepository logRepository;

    @Inject
    ObjectMapper objectMapper;

    @GET
    public List<TrafficLog> list(@QueryParam("limit") @DefaultValue("100") int limit,
                                     @QueryParam("profileId") Long profileId,
                                     @QueryParam("instanceId") Long instanceId) {
        if (instanceId != null) {
            return logRepository.findByInstanceId(instanceId, Math.min(limit, 1000));
        }
        if (profileId != null) {
            return logRepository.findByProfileId(profileId, Math.min(limit, 1000));
        }
        return logRepository.listRecent(Math.min(limit, 1000));
    }

    @GET
    @Path("/{id}/friendly")
    public Response getFriendly(@PathParam("id") Long id) {
        return logRepository.findById(id)
            .map(this::toFriendly)
            .map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        return logRepository.findById(id)
            .map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = logRepository.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    @POST
    @Path("/clear")
    @Transactional
    public Response clear() {
        long count = logRepository.deleteAll();
        return Response.ok(Map.of("deleted", count)).build();
    }

    private TrafficLogFriendlyDto toFriendly(TrafficLog log) {
        TrafficLogFriendlyDto dto = new TrafficLogFriendlyDto();
        dto.id = log.id;
        dto.instanceId = log.instanceId;
        dto.instanceSlug = log.instanceSlug;
        dto.profileId = log.profileId;
        dto.requestMethod = log.requestMethod;
        dto.requestPath = log.requestPath;
        dto.requestTime = log.requestTime;
        dto.responseStatus = log.responseStatus;
        dto.durationMs = log.durationMs;
        dto.responseTime = log.responseTime;
        dto.requestBody = parseJson(log.requestBody);
        dto.responseBody = parseJson(log.responseBody);
        dto.rawRequestHeaders = log.requestHeaders;
        dto.rawResponseHeaders = log.responseHeaders;
        dto.rawPluginLogs = log.pluginLogs;

        dto.userPrompt = extractUserPrompt(dto.requestBody);
        dto.modelOutput = extractModelOutput(dto.responseBody);
        dto.model = extractModel(dto.requestBody);

        dto.requestPipeline = new ArrayList<>();
        dto.responsePipeline = new ArrayList<>();
        List<PluginExecutionSnapshot> snapshots = parseSnapshots(log.friendlyPluginSnapshots);
        if (snapshots != null) {
            for (PluginExecutionSnapshot s : snapshots) {
                if ("REQUEST".equalsIgnoreCase(s.phase)) {
                    dto.requestPipeline.add(s);
                } else {
                    dto.responsePipeline.add(s);
                }
            }
        }
        return dto;
    }

    private Object parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (IOException e) {
            return value;
        }
    }

    private List<PluginExecutionSnapshot> parseSnapshots(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(value, objectMapper.getTypeFactory().constructCollectionType(List.class, PluginExecutionSnapshot.class));
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private String extractUserPrompt(Object requestBody) {
        if (!(requestBody instanceof Map)) {
            return null;
        }
        Object messages = ((Map<?, ?>) requestBody).get("messages");
        if (!(messages instanceof List)) {
            return null;
        }
        for (Object m : (List<?>) messages) {
            if (m instanceof Map) {
                Map<?, ?> mm = (Map<?, ?>) m;
                if ("user".equals(mm.get("role"))) {
                    Object content = mm.get("content");
                    if (content instanceof String) {
                        return (String) content;
                    }
                    if (content instanceof List) {
                        StringBuilder sb = new StringBuilder();
                        for (Object part : (List<?>) content) {
                            if (part instanceof Map) {
                                Object type = ((Map<?, ?>) part).get("type");
                                Object text = ((Map<?, ?>) part).get("text");
                                if ("text".equals(type) && text instanceof String) {
                                    sb.append(text);
                                }
                            }
                        }
                        return sb.toString();
                    }
                }
            }
        }
        return null;
    }

    private String extractModelOutput(Object responseBody) {
        if (!(responseBody instanceof Map)) {
            return null;
        }
        Map<?, ?> body = (Map<?, ?>) responseBody;
        Object choices = body.get("choices");
        if (!(choices instanceof List) || ((List<?>) choices).isEmpty()) {
            return null;
        }
        Object first = ((List<?>) choices).get(0);
        if (!(first instanceof Map)) {
            return null;
        }
        Object message = ((Map<?, ?>) first).get("message");
        if (message instanceof Map) {
            Object content = ((Map<?, ?>) message).get("content");
            if (content instanceof String) {
                return (String) content;
            }
        }
        Object delta = ((Map<?, ?>) first).get("delta");
        if (delta instanceof Map) {
            Object content = ((Map<?, ?>) delta).get("content");
            if (content instanceof String) {
                return (String) content;
            }
        }
        return null;
    }

    private String extractModel(Object requestBody) {
        if (!(requestBody instanceof Map)) {
            return null;
        }
        Object model = ((Map<?, ?>) requestBody).get("model");
        return model instanceof String ? (String) model : null;
    }
}

