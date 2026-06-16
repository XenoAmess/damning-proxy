package com.xenoamess.damning_proxy.api.admin;

import com.xenoamess.damning_proxy.entity.TrafficLog;
import com.xenoamess.damning_proxy.repository.LogRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/api/logs")
@Produces(MediaType.APPLICATION_JSON)
public class LogAdminApi {

    @Inject
    LogRepository logRepository;

    @GET
    public List<TrafficLog> list(@QueryParam("limit") @DefaultValue("100") int limit,
                                   @QueryParam("profileId") Long profileId) {
        if (profileId != null) {
            return logRepository.findByProfileId(profileId, Math.min(limit, 1000));
        }
        return logRepository.listRecent(Math.min(limit, 1000));
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
}
