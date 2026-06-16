package com.xenoamess.damning_proxy.proxy;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/proxy/{profileId}")
public class ProxyApi {

    @Inject
    OpenAiProxyService proxyService;

    @GET
    @Path("/models")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listModels(@PathParam("profileId") String profileId) {
        return proxyService.listModels(profileId);
    }

    @POST
    @Path("/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.SERVER_SENT_EVENTS})
    public Response chatCompletions(@PathParam("profileId") String profileId, Object requestBody) {
        return proxyService.chatCompletions(profileId, requestBody);
    }
}
