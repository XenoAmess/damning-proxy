package com.xenoamess.damning_proxy.api;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/v1/health")
public class HealthResource {

    @Inject
    EntityManager em;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        boolean dbOk = false;
        try {
            em.createNativeQuery("SELECT 1").getSingleResult();
            dbOk = true;
        } catch (Exception e) {
            Log.error("Health check: database connectivity failed", e);
        }
        return Response.ok(Map.of(
            "status", dbOk ? "ok" : "degraded",
            "database", dbOk ? "ok" : "error"
        )).build();
    }
}
