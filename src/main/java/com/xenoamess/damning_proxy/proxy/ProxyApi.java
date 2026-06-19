package com.xenoamess.damning_proxy.proxy;

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

    private static boolean isStreamingRequest(Map<String, Object> requestBody) {
        if (requestBody == null) {
            return false;
        }
        Object stream = requestBody.get("stream");
        return Boolean.TRUE.equals(stream) || "true".equals(stream);
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
    @Produces({MediaType.APPLICATION_JSON, MediaType.SERVER_SENT_EVENTS})
    public Object chatCompletions(@PathParam("instanceSlug") String instanceSlug, Map<String, Object> requestBody,
                                     @Context HttpHeaders headers) {
        boolean wantsStream = isStreamingRequest(requestBody)
            || headers.getAcceptableMediaTypes().stream().anyMatch(mt -> mt.isCompatible(MediaType.SERVER_SENT_EVENTS_TYPE));
        if (wantsStream) {
            return proxyService.chatCompletionsStream(instanceSlug, requestBody, headers);
        }
        return proxyService.chatCompletions(instanceSlug, requestBody, headers);
    }
}
