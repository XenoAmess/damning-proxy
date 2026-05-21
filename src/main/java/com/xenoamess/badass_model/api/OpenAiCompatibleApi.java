package com.xenoamess.badass_model.api;

import com.xenoamess.badass_model.dto.*;
import com.xenoamess.badass_model.service.MockLlmService;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;
import java.util.concurrent.Flow;

@Path("/v1")
public class OpenAiCompatibleApi {

    @Inject
    MockLlmService mockLlmService;

    @POST
    @Path("/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Flow.Publisher<String> chatCompletions(ChatCompletionRequest request, @Context HttpHeaders headers) {
        Log.infof("Chat completion request: model=%s, stream=%s, messages=%d",
            request.getModel(), request.getStream(),
            request.getMessages() != null ? request.getMessages().size() : 0);

        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new BadRequestException("messages field is required");
        }

        if (request.getStream() == null || !request.getStream()) {
            // For non-streaming, we still return SSE but with a single event
            // Or you could return JSON directly - OpenCode always uses streaming
            Log.warn("Non-streaming request received - OpenCode always uses streaming");
        }

        // Get working directory from OpenCode header
        String workdir = headers.getHeaderString("x-opencode-directory");
        if (workdir != null) {
            try {
                workdir = java.net.URLDecoder.decode(workdir, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                Log.warnf("Failed to decode x-opencode-directory header: %s", workdir);
            }
        }

        return mockLlmService.streamChatCompletion(request, workdir);
    }

    @GET
    @Path("/models")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listModels() {
        Log.info("Models list request");

        List<ModelListResponse.Model> models = List.of(
            new ModelListResponse.Model(
                "mock-gpt-4o",
                "model",
                1700000000L,
                "badass-model"
            ),
            new ModelListResponse.Model(
                "mock-claude-3-sonnet",
                "model",
                1700000000L,
                "badass-model"
            ),
            new ModelListResponse.Model(
                "mock-llama-3-70b",
                "model",
                1700000000L,
                "badass-model"
            )
        );

        ModelListResponse response = new ModelListResponse("list", models);
        return Response.ok(response).build();
    }

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        return Response.ok(new HealthStatus("ok")).build();
    }

    public static class HealthStatus {
        public String status;
        public HealthStatus(String status) { this.status = status; }
    }
}
