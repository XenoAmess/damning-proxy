package com.xenoamess.damning_proxy.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xenoamess.damning_proxy.dto.ChatMessage;
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
        dto.requestBodyLength = log.requestBodyLength;
        dto.responseBodyLength = log.responseBodyLength;
        dto.upstreamBaseUrl = log.upstreamBaseUrl;
        dto.timeoutMs = log.timeoutMs;
        dto.streaming = log.streaming;
        dto.errorMessage = log.errorMessage;
        dto.rawRequestHeaders = log.requestHeaders;
        dto.rawResponseHeaders = log.responseHeaders;
        dto.rawPluginLogs = log.pluginLogs;

        dto.userPrompt = extractUserPrompt(dto.requestBody);
        dto.modelOutput = extractModelOutput(dto.responseBody);
        dto.model = extractModel(dto.requestBody);

        // Surface the entire conversation, not just the first user/assistant turn,
        // so the frontend 对话摘要 tab can render system + user + assistant history.
        dto.requestMessages = extractRequestMessages(dto.requestBody);
        dto.responseMessages = extractResponseMessages(dto.responseBody);

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

    /**
     * Walk the request {@code messages} array and return one {@link ChatMessage}
     * per entry, in original order. {@code content} can be a string or an array
     * of parts ({@code {type:"text", text:"..."}} or {@code {type:"image_url", ...}});
     * text parts are concatenated in order so the frontend gets a single readable
     * string, and very long messages carry a contentLength hint.
     */
    private List<ChatMessage> extractRequestMessages(Object requestBody) {
        List<ChatMessage> result = new ArrayList<>();
        if (!(requestBody instanceof Map)) {
            return result;
        }
        Object messages = ((Map<?, ?>) requestBody).get("messages");
        if (!(messages instanceof List)) {
            return result;
        }
        for (Object m : (List<?>) messages) {
            ChatMessage cm = readMessage(m);
            if (cm != null) {
                result.add(cm);
            }
        }
        return result;
    }

    /**
     * Walk the response {@code choices} array (one entry per choice/stream
     * reconstruction) and return one {@link ChatMessage} per assistant message.
     */
    private List<ChatMessage> extractResponseMessages(Object responseBody) {
        List<ChatMessage> result = new ArrayList<>();
        if (!(responseBody instanceof Map)) {
            return result;
        }
        Object choices = ((Map<?, ?>) responseBody).get("choices");
        if (!(choices instanceof List)) {
            return result;
        }
        for (Object choice : (List<?>) choices) {
            if (!(choice instanceof Map)) {
                continue;
            }
            Map<?, ?> cm = (Map<?, ?>) choice;
            Object message = cm.get("message");
            if (message instanceof Map) {
                ChatMessage chat = readMessage(message);
                if (chat != null) {
                    result.add(chat);
                    continue;
                }
            }
            // Stream responses carry `delta` instead of `message`.
            Object delta = cm.get("delta");
            if (delta instanceof Map) {
                ChatMessage chat = readMessage(delta);
                if (chat != null) {
                    result.add(chat);
                }
            }
        }
        return result;
    }

    /**
     * Convert one message-shaped object into a {@link ChatMessage}. Accepts the
     * common shapes: plain {@code {role, content}}, multimodal
     * {@code {role, content:[{type, text}, ...]}}, and stream deltas with
     * {@code reasoning_content} / {@code audio_content} fields.
     */
    private ChatMessage readMessage(Object raw) {
        if (!(raw instanceof Map)) {
            return null;
        }
        Map<?, ?> m = (Map<?, ?>) raw;
        ChatMessage cm = new ChatMessage();
        Object role = m.get("role");
        if (role instanceof String) {
            cm.role = (String) role;
        }
        Object name = m.get("name");
        if (name instanceof String) {
            cm.name = (String) name;
        }
        Object reasoning = m.get("reasoning_content");
        if (reasoning instanceof String) {
            cm.reasoningContent = (String) reasoning;
        }
        Object content = m.get("content");
        if (content instanceof String) {
            cm.content = (String) content;
            cm.contentLength = cm.content.length();
        } else if (content instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object part : (List<?>) content) {
                if (part instanceof Map) {
                    Map<?, ?> pm = (Map<?, ?>) part;
                    Object type = pm.get("type");
                    Object text = pm.get("text");
                    if ("text".equals(type) && text instanceof String) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(text);
                    }
                }
            }
            cm.content = sb.toString();
            cm.contentLength = cm.content.length();
        }
        Object toolCalls = m.get("tool_calls");
        if (toolCalls instanceof List) {
            List<String> ids = new ArrayList<>();
            for (Object tc : (List<?>) toolCalls) {
                if (tc instanceof Map) {
                    Object id = ((Map<?, ?>) tc).get("id");
                    if (id instanceof String) {
                        ids.add((String) id);
                    }
                }
            }
            if (!ids.isEmpty()) {
                cm.toolCallIds = ids;
            }
        }
        if (cm.role == null && cm.content == null && cm.reasoningContent == null
            && cm.toolCallIds == null) {
            return null;
        }
        return cm;
    }
}

