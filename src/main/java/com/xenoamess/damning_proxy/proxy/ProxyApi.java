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
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/v1/proxy/{instanceSlug}")
public class ProxyApi {

    @Inject
    OpenAiProxyService proxyService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    RateLimiter rateLimiter;

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

    private static Response rateLimitedResponse(String instanceSlug, RateLimiter.RateLimitInfo info) {
        return Response.status(429)
            .header("RateLimit-Limit", String.valueOf(info.limit()))
            .header("RateLimit-Remaining", String.valueOf(info.remaining()))
            .header("RateLimit-Reset", String.valueOf(info.resetSeconds()))
            .entity(Map.of("error", "Rate limit exceeded for instance: " + instanceSlug))
            .build();
    }

    private Response withRateLimitHeaders(Response response, RateLimiter.RateLimitInfo info) {
        return Response.fromResponse(response)
            .header("RateLimit-Limit", String.valueOf(info.limit()))
            .header("RateLimit-Remaining", String.valueOf(info.remaining()))
            .header("RateLimit-Reset", String.valueOf(info.resetSeconds()))
            .build();
    }

    private Response buildRateLimitedResponse(String instanceSlug) {
        RateLimiter.RateLimitInfo info = rateLimiter.getRateLimitInfo(instanceSlug);
        return rateLimitedResponse(instanceSlug, info);
    }

    @GET
    @Path("/models")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listModels(@PathParam("instanceSlug") String instanceSlug, @Context HttpHeaders headers) {
        if (!rateLimiter.tryAcquire(instanceSlug)) {
            return buildRateLimitedResponse(instanceSlug);
        }
        return withRateLimitHeaders(proxyService.listModels(instanceSlug, headers),
            rateLimiter.getRateLimitInfo(instanceSlug));
    }

    @POST
    @Path("/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Blocking
    public Response chatCompletions(@PathParam("instanceSlug") String instanceSlug, Map<String, Object> requestBody,
                                     @Context HttpHeaders headers) {
        if (!rateLimiter.tryAcquire(instanceSlug)) {
            return buildRateLimitedResponse(instanceSlug);
        }
        RateLimiter.RateLimitInfo info = rateLimiter.getRateLimitInfo(instanceSlug);
        boolean wantsStream = isStreamingRequest(requestBody)
            || headers.getAcceptableMediaTypes().stream().anyMatch(mt -> {
                return mt.isCompatible(MediaType.SERVER_SENT_EVENTS_TYPE)
                    && !mt.isWildcardType() && !mt.isWildcardSubtype();
            });
        if (wantsStream) {
            requestBody.put("stream", true);
            Multi<String> sseMulti = proxyService.chatCompletionsStream(instanceSlug, requestBody, headers);
            StreamingOutput output = new SseStreamingOutput(sseMulti);
            return withRateLimitHeaders(Response.ok(output)
                .type(MediaType.SERVER_SENT_EVENTS)
                .build(), info);
        }
        return withRateLimitHeaders(proxyService.chatCompletions(instanceSlug, requestBody, headers), info);
    }

    @POST
    @Path("/embeddings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Blocking
    public Response embeddings(@PathParam("instanceSlug") String instanceSlug, Map<String, Object> requestBody,
                                @Context HttpHeaders headers) {
        if (!rateLimiter.tryAcquire(instanceSlug)) {
            return buildRateLimitedResponse(instanceSlug);
        }
        return withRateLimitHeaders(proxyService.embeddings(instanceSlug, requestBody, headers),
            rateLimiter.getRateLimitInfo(instanceSlug));
    }

    @POST
    @Path("/images/generations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Blocking
    public Response imageGenerations(@PathParam("instanceSlug") String instanceSlug, Map<String, Object> requestBody,
                                      @Context HttpHeaders headers) {
        if (!rateLimiter.tryAcquire(instanceSlug)) {
            return buildRateLimitedResponse(instanceSlug);
        }
        return withRateLimitHeaders(proxyService.imageGenerations(instanceSlug, requestBody, headers),
            rateLimiter.getRateLimitInfo(instanceSlug));
    }

    @POST
    @Path("/audio/speech")
    @Consumes(MediaType.APPLICATION_JSON)
    @Blocking
    public Response audioSpeech(@PathParam("instanceSlug") String instanceSlug, Map<String, Object> requestBody,
                                @Context HttpHeaders headers) {
        if (!rateLimiter.tryAcquire(instanceSlug)) {
            return buildRateLimitedResponse(instanceSlug);
        }
        RateLimiter.RateLimitInfo info = rateLimiter.getRateLimitInfo(instanceSlug);
        return withRateLimitHeaders(proxyService.audioSpeech(instanceSlug, requestBody, headers), info);
    }

    @POST
    @Path("/audio/transcriptions")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Blocking
    public Response audioTranscriptions(@PathParam("instanceSlug") String instanceSlug,
                                        @FormParam("file") FileUpload file,
                                        @FormParam("model") String model,
                                        @FormParam("language") String language,
                                        @FormParam("prompt") String prompt,
                                        @FormParam("response_format") String responseFormat,
                                        @FormParam("temperature") String temperature,
                                        @Context HttpHeaders headers) {
        if (!rateLimiter.tryAcquire(instanceSlug)) {
            return buildRateLimitedResponse(instanceSlug);
        }
        RateLimiter.RateLimitInfo info = rateLimiter.getRateLimitInfo(instanceSlug);
        UpstreamHttpClient.MultipartData multipart = buildMultipart(file, model, language, prompt, responseFormat, temperature);
        return withRateLimitHeaders(proxyService.audioTranscriptions(instanceSlug, multipart, headers), info);
    }

    @POST
    @Path("/audio/translations")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Blocking
    public Response audioTranslations(@PathParam("instanceSlug") String instanceSlug,
                                      @FormParam("file") FileUpload file,
                                      @FormParam("model") String model,
                                      @FormParam("prompt") String prompt,
                                      @FormParam("response_format") String responseFormat,
                                      @FormParam("temperature") String temperature,
                                      @Context HttpHeaders headers) {
        if (!rateLimiter.tryAcquire(instanceSlug)) {
            return buildRateLimitedResponse(instanceSlug);
        }
        RateLimiter.RateLimitInfo info = rateLimiter.getRateLimitInfo(instanceSlug);
        UpstreamHttpClient.MultipartData multipart = buildMultipart(file, model, null, prompt, responseFormat, temperature);
        return withRateLimitHeaders(proxyService.audioTranslations(instanceSlug, multipart, headers), info);
    }

    private UpstreamHttpClient.MultipartData buildMultipart(FileUpload file, String model, String language,
                                                            String prompt, String responseFormat, String temperature) {
        if (file == null || !Files.exists(file.uploadedFile())) {
            throw new WebApplicationException("Audio file is required", Response.Status.BAD_REQUEST);
        }
        try {
            byte[] data = Files.readAllBytes(file.uploadedFile());
            MultipartBuilder builder = new MultipartBuilder()
                .file("file", file.fileName(), file.contentType(), data)
                .field("model", model);
            if (language != null) {
                builder.field("language", language);
            }
            if (prompt != null) {
                builder.field("prompt", prompt);
            }
            if (responseFormat != null) {
                builder.field("response_format", responseFormat);
            }
            if (temperature != null) {
                builder.field("temperature", temperature);
            }
            return new UpstreamHttpClient.MultipartData(builder.build(), builder.contentType());
        } catch (IOException e) {
            throw new WebApplicationException("Failed to read uploaded audio file", e, Response.Status.BAD_REQUEST);
        }
    }

    private class SseStreamingOutput implements StreamingOutput {
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
                    try {
                        String event = OpenAiProxyService.SSE_ERROR_PREFIX +
                            OpenAiProxyService.SSE_DATA_PREFIX + objectMapper.writeValueAsString(Map.of("error", Map.of("message", err.getMessage()))) + OpenAiProxyService.SSE_NEWLINE;
                        output.write(event.getBytes(StandardCharsets.UTF_8));
                        output.flush();
                    } catch (Exception writeErr) {
                        Log.debug("Failed to write SSE error event", writeErr);
                    } finally {
                        done.countDown();
                    }
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
