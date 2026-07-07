package com.xenoamess.damning_proxy.api.admin;

import com.xenoamess.damning_proxy.entity.GlobalSettings;
import com.xenoamess.damning_proxy.repository.GlobalSettingsRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/settings/rate-limit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GlobalSettingsAdminApi {

    @Inject
    GlobalSettingsRepository globalSettingsRepository;

    @GET
    public Response get() {
        GlobalSettings settings = globalSettingsRepository.getOrCreateSingleton();
        return Response.ok(toResponse(settings)).build();
    }

    @PUT
    @Transactional
    public Response update(RateLimitForm form) {
        if (form == null || form.maxRequestsPerWindow == null || form.windowSeconds == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("maxRequestsPerWindow and windowSeconds are required").build();
        }
        if (form.maxRequestsPerWindow < 1 || form.maxRequestsPerWindow > 1_000_000) {
            return Response.status(Response.Status.BAD_REQUEST).entity("maxRequestsPerWindow must be between 1 and 1000000").build();
        }
        if (form.windowSeconds < 1 || form.windowSeconds > 86_400) {
            return Response.status(Response.Status.BAD_REQUEST).entity("windowSeconds must be between 1 and 86400").build();
        }
        GlobalSettings settings = globalSettingsRepository.getOrCreateSingleton();
        settings.maxRequestsPerWindow = form.maxRequestsPerWindow;
        settings.windowSeconds = form.windowSeconds;
        globalSettingsRepository.save(settings);
        return Response.ok(toResponse(settings)).build();
    }

    private RateLimitResponse toResponse(GlobalSettings settings) {
        return new RateLimitResponse(
            settings.id,
            settings.maxRequestsPerWindow,
            settings.windowSeconds,
            settings.createdAt,
            settings.updatedAt);
    }

    public record RateLimitForm(Integer maxRequestsPerWindow, Integer windowSeconds) {
    }

    public record RateLimitResponse(Long id, Integer maxRequestsPerWindow, Integer windowSeconds,
                                     java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt) {
    }
}
