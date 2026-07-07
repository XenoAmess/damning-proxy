package com.xenoamess.damning_proxy.api;

import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.proxy.CircuitBreaker;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/v1/health")
public class HealthResource {

    @Inject
    EntityManager em;

    @Inject
    CircuitBreaker circuitBreaker;

    @Inject
    ProfileRepository profileRepository;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

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

        Map<String, Object> upstreams = new LinkedHashMap<>();
        boolean allUpstreamsUp = true;
        for (ProxyProfile profile : profileRepository.listAll()) {
            if (!profile.enabled) {
                Map<String, Object> status = new LinkedHashMap<>();
                status.put("enabled", false);
                status.put("status", "disabled");
                upstreams.put(profile.slug, status);
                continue;
            }
            Map<String, Object> status = probeUpstream(profile);
            if (!"up".equals(status.get("status"))) {
                allUpstreamsUp = false;
            }
            upstreams.put(profile.slug, status);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        boolean overallOk = dbOk && allUpstreamsUp;
        result.put("status", overallOk ? "ok" : "degraded");
        result.put("database", dbOk ? "ok" : "error");
        result.put("upstreams", upstreams);
        result.put("circuitBreakers", circuitBreaker.getSnapshot());
        return Response.ok(result).build();
    }

    private Map<String, Object> probeUpstream(ProxyProfile profile) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", true);
        status.put("baseUrl", profile.baseUrl);
        try {
            URI uri = URI.create(profile.baseUrl + "/models");
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            status.put("statusCode", code);
            if (code < 500) {
                status.put("status", "up");
            } else {
                status.put("status", "down");
                status.put("error", "Upstream returned HTTP " + code);
            }
        } catch (Exception e) {
            status.put("status", "down");
            status.put("error", e.getMessage());
        }
        return status;
    }
}
