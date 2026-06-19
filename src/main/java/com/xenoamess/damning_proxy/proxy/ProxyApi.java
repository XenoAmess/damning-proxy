package com.xenoamess.damning_proxy.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

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
            // Force upstream stream=true so non-stream requests don't get confused; let plugins override if needed
            requestBody.put("stream", true);
            return Response.ok(proxyService.chatCompletionsStream(instanceSlug, requestBody, headers))
                .type(MediaType.SERVER_SENT_EVENTS)
                .build();
        }
        return proxyService.chatCompletions(instanceSlug, requestBody, headers);
    }
}
