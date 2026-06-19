package com.xenoamess.damning_proxy.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@Path("/v1/proxy/{instanceSlug}")
public class ProxyApi {

    @Inject
    OpenAiProxyService proxyService;

    @Inject
    ObjectMapper objectMapper;

    private static boolean isStreamingRequest(Map<String, Object> requestBody) {
        if (requestBody == null) {
            return false;
        }
        Object stream = requestBody.get("stream");
        if (stream == null) {
            return false;
        }
        if (stream instanceof Boolean) {
            return (Boolean) stream;
        }
        if (stream instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    @GET
    @Path("/models")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listModels(@PathParam("instanceSlug") String instanceSlug, @Context HttpHeaders headers) {
        return proxyService.listModels(instanceSlug, headers);
    }

    @POST
    @Path("/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Blocking
    public Response chatCompletions(@PathParam("instanceSlug") String instanceSlug, Map<String, Object> requestBody,
                                     @Context HttpHeaders headers) {
        boolean wantsStream = isStreamingRequest(requestBody)
            || headers.getAcceptableMediaTypes().stream().anyMatch(mt -> {
                return mt.isCompatible(MediaType.SERVER_SENT_EVENTS_TYPE)
                    && !mt.isWildcardType() && !mt.isWildcardSubtype();
            });
        if (wantsStream) {
            requestBody.put("stream", true);
            Multi<String> sseMulti = proxyService.chatCompletionsStream(instanceSlug, requestBody, headers);
            StreamingOutput output = new SseStreamingOutput(sseMulti);
            return Response.ok(output)
                .type(MediaType.SERVER_SENT_EVENTS)
                .build();
        }
        return proxyService.chatCompletions(instanceSlug, requestBody, headers);
    }

    private static class SseStreamingOutput implements StreamingOutput {
        private final Multi<String> sseMulti;

        SseStreamingOutput(Multi<String> sseMulti) {
            this.sseMulti = sseMulti;
        }

        @Override
        public void write(OutputStream output) throws IOException {
            CountDownLatch done = new CountDownLatch(1);
            sseMulti.subscribe().with(
                item -> {
                    try {
                        output.write(item.getBytes(StandardCharsets.UTF_8));
                        output.flush();
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                },
                err -> {
                    Log.error("SSE stream error", err);
                    done.countDown();
                },
                done::countDown
            );
            try {
                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while streaming", e);
            }
        }
    }
}
