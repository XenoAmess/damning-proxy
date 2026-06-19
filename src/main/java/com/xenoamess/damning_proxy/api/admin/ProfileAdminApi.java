package com.xenoamess.damning_proxy.api.admin;

import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
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
    public Response create(ProxyProfile profile) {
        if (profile.slug == null || profile.slug.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("slug is required").build();
        }
        if (profileRepository.findBySlug(profile.slug).isPresent()) {
            return Response.status(Response.Status.CONFLICT).entity("slug already exists").build();
        }
        profileRepository.save(profile);
        return Response.status(Response.Status.CREATED).entity(profile).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, ProxyProfile profile) {
        return profileRepository.findById(id)
            .map(existing -> {
                profile.id = id;
                if (profile.slug == null || profile.slug.isBlank()) {
                    return Response.status(Response.Status.BAD_REQUEST).entity("slug is required").build();
                }
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
            if (ep.slug == null || ep.slug.isBlank() || ep.baseUrl == null || ep.baseUrl.isBlank()) {
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
            profile.enabled = ep.enabled;
            profileRepository.save(profile);
            imported++;
        }
        return Response.ok(new ImportResult(imported, skipped)).build();
    }

    public record ExportRequest(List<Long> ids) {
    }

    public record ExportProfile(String name, String slug, String baseUrl, String bearerToken,
                                String customHeaders, String customBody, String defaultModel, Integer timeoutMs, boolean enabled) {
    }

    public record ImportResult(int imported, int skipped) {
    }
}
