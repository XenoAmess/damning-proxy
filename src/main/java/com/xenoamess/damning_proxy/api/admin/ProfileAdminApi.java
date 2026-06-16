package com.xenoamess.damning_proxy.api.admin;

import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

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
}
