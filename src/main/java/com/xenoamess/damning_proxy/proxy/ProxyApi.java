package com.xenoamess.damning_proxy.proxy;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
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
    public Response listModels(@PathParam("instanceSlug") String instanceSlug) {
        return proxyService.listModels(instanceSlug);
    }

    @POST
    @Path("/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response chatCompletions(@PathParam("instanceSlug") String instanceSlug, Object requestBody) {
        return proxyService.chatCompletions(instanceSlug, requestBody);
    }

    @POST
    @Path("/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    @Blocking
    public Multi<String> chatCompletionsStream(@PathParam("instanceSlug") String instanceSlug,
                                                  Map<String, Object> requestBody) {
        return proxyService.chatCompletionsStream(instanceSlug, requestBody);
    }
}
