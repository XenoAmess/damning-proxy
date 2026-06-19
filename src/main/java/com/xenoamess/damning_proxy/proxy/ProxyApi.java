package com.xenoamess.damning_proxy.proxy;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.Map;

@Path("/v1/proxy/{instanceSlug}")
public class ProxyApi {

    @Inject
    OpenAiProxyService proxyService;

    @GET
    @Path("/models")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listModels(@PathParam("instanceSlug") String instanceSlug, @Context HttpHeaders headers) {
        return proxyService.listModels(instanceSlug, headers);
    }

    @POST
    @Path("/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response chatCompletions(@PathParam("instanceSlug") String instanceSlug, Object requestBody,
                                    @Context HttpHeaders headers) {
        return proxyService.chatCompletions(instanceSlug, requestBody, headers);
    }

    @POST
    @Path("/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    @Blocking
    public Multi<String> chatCompletionsStream(@PathParam("instanceSlug") String instanceSlug,
                                               Map<String, Object> requestBody,
                                               @Context HttpHeaders headers) {
        return proxyService.chatCompletionsStream(instanceSlug, requestBody, headers);
    }
}
