package com.xenoamess.damning_proxy.api.admin;

import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import com.xenoamess.damning_proxy.util.Validation;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/profiles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfileAdminApi {

    @Inject
    ProfileRepository profileRepository;

    @GET
    public List<ProxyProfile> list() {
        return profileRepository.listAll();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        return profileRepository.findById(id)
            .map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Transactional
    public Response create(ProfileForm form) {
        Validation.validateSlug(form.slug);
        if (profileRepository.findBySlug(form.slug).isPresent()) {
            return Response.status(Response.Status.CONFLICT).entity("slug already exists").build();
        }
        ProxyProfile profile = toEntity(form);
        profileRepository.save(profile);
        return Response.status(Response.Status.CREATED).entity(profile).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, ProfileForm form) {
        return profileRepository.findById(id)
            .map(existing -> {
                Validation.validateSlug(form.slug);
                if (profileRepository.findBySlug(form.slug).map(p -> !p.id.equals(id)).orElse(false)) {
                    return Response.status(Response.Status.CONFLICT).entity("slug already exists").build();
                }
                ProxyProfile profile = toEntity(form);
                profile.id = id;
                profileRepository.save(profile);
                return Response.ok(profile).build();
            })
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = profileRepository.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    @POST
    @Path("/export")
    @Produces(MediaType.APPLICATION_JSON)
    public Response export(ExportRequest request) {
        List<ProxyProfile> profiles;
        if (request != null && request.ids != null && !request.ids.isEmpty()) {
            profiles = request.ids.stream()
                .map(pid -> profileRepository.findById(pid).orElse(null))
                .filter(p -> p != null)
                .collect(Collectors.toList());
        } else {
            profiles = profileRepository.listAll();
        }
        List<ExportProfile> exportProfiles = profiles.stream()
            .map(p -> new ExportProfile(
                p.name,
                p.slug,
                p.baseUrl,
                p.bearerToken,
                p.customHeaders,
                p.customBody,
                p.defaultModel,
                p.timeoutMs,
                p.circuitBreakerFailureThreshold,
                p.circuitBreakerOpenTimeoutSeconds,
                p.enabled))
            .collect(Collectors.toList());
        return Response.ok(exportProfiles).build();
    }

    @POST
    @Path("/import")
    @Transactional
    public Response importProfiles(List<ExportProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("No profiles to import").build();
        }
        int imported = 0;
        int skipped = 0;
        for (ExportProfile ep : profiles) {
            Validation.validateSlug(ep.slug);
            if (ep.baseUrl == null || ep.baseUrl.isBlank()) {
                continue;
            }
            if (profileRepository.findBySlug(ep.slug).isPresent()) {
                skipped++;
                continue;
            }
            ProxyProfile profile = new ProxyProfile();
            profile.name = ep.name;
            profile.slug = ep.slug;
            profile.baseUrl = ep.baseUrl;
            profile.bearerToken = ep.bearerToken;
            profile.customHeaders = ep.customHeaders;
            profile.customBody = ep.customBody;
            profile.defaultModel = ep.defaultModel;
            profile.timeoutMs = ep.timeoutMs;
            profile.circuitBreakerFailureThreshold = ep.circuitBreakerFailureThreshold != null ? ep.circuitBreakerFailureThreshold : profile.circuitBreakerFailureThreshold;
            profile.circuitBreakerOpenTimeoutSeconds = ep.circuitBreakerOpenTimeoutSeconds != null ? ep.circuitBreakerOpenTimeoutSeconds : profile.circuitBreakerOpenTimeoutSeconds;
            profile.enabled = ep.enabled;
            profileRepository.save(profile);
            imported++;
        }
        return Response.ok(new ImportResult(imported, skipped)).build();
    }

    private ProxyProfile toEntity(ProfileForm form) {
        ProxyProfile profile = new ProxyProfile();
        profile.name = form.name;
        profile.slug = form.slug;
        profile.baseUrl = form.baseUrl;
        profile.bearerToken = form.bearerToken;
        profile.customHeaders = form.customHeaders;
        profile.customBody = form.customBody;
        profile.defaultModel = form.defaultModel;
        profile.timeoutMs = form.timeoutMs != null ? form.timeoutMs : profile.timeoutMs;
        profile.circuitBreakerFailureThreshold = form.circuitBreakerFailureThreshold != null ? form.circuitBreakerFailureThreshold : profile.circuitBreakerFailureThreshold;
        profile.circuitBreakerOpenTimeoutSeconds = form.circuitBreakerOpenTimeoutSeconds != null ? form.circuitBreakerOpenTimeoutSeconds : profile.circuitBreakerOpenTimeoutSeconds;
        profile.enabled = form.enabled;
        return profile;
    }

    public record ProfileForm(String name, String slug, String baseUrl, String bearerToken,
                               String customHeaders, String customBody, String defaultModel,
                               Integer timeoutMs, Integer circuitBreakerFailureThreshold,
                               Integer circuitBreakerOpenTimeoutSeconds, boolean enabled) {
    }

    public record ExportRequest(List<Long> ids) {
    }

    public record ExportProfile(String name, String slug, String baseUrl, String bearerToken,
                                 String customHeaders, String customBody, String defaultModel, Integer timeoutMs,
                                 Integer circuitBreakerFailureThreshold, Integer circuitBreakerOpenTimeoutSeconds, boolean enabled) {
    }

    public record ImportResult(int imported, int skipped) {
    }
}
