package com.xenoamess.damning_proxy.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/")
public class HomeResource {

    @GET
    public Response redirectToAdmin() {
        return Response.temporaryRedirect(java.net.URI.create("/admin/index.html")).build();
    }
}
