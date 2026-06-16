package com.xenoamess.damning_proxy.api.admin;

import com.xenoamess.damning_proxy.entity.ProxyInstance;
import com.xenoamess.damning_proxy.repository.InstanceRepository;
import com.xenoamess.damning_proxy.repository.PluginGroupRepository;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Optional;

@Path("/api/instances")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InstanceAdminApi {

    @Inject
    InstanceRepository instanceRepository;

    @Inject
    ProfileRepository profileRepository;

    @Inject
    PluginGroupRepository pluginGroupRepository;

    @GET
    public List<ProxyInstance> list() {
        return instanceRepository.listAll();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        return instanceRepository.findById(id)
            .map(i -> Response.ok(i).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Transactional
    public Response create(InstanceRequest request) {
        if (request.slug == null || request.slug.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("slug is required").build();
        }
        if (instanceRepository.findBySlug(request.slug).isPresent()) {
            return Response.status(Response.Status.CONFLICT).entity("slug already exists").build();
        }
        Response validation = validate(request);
        if (validation != null) {
            return validation;
        }
        ProxyInstance instance = toEntity(request);
        instanceRepository.save(instance);
        return Response.status(Response.Status.CREATED).entity(instance).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, InstanceRequest request) {
        Optional<ProxyInstance> existingOpt = instanceRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (request.slug == null || request.slug.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("slug is required").build();
        }
        Optional<ProxyInstance> bySlug = instanceRepository.findBySlug(request.slug);
        if (bySlug.isPresent() && !bySlug.get().id.equals(id)) {
            return Response.status(Response.Status.CONFLICT).entity("slug already exists").build();
        }
        Response validation = validate(request);
        if (validation != null) {
            return validation;
        }
        ProxyInstance instance = toEntity(request);
        instance.id = id;
        instanceRepository.save(instance);
        return Response.ok(instance).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = instanceRepository.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private Response validate(InstanceRequest request) {
        if (request.profileId == null || profileRepository.findById(request.profileId).isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("profileId is required and must exist").build();
        }
        if (request.pluginGroupId == null || pluginGroupRepository.findById(request.pluginGroupId).isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("pluginGroupId is required and must exist").build();
        }
        return null;
    }

    private ProxyInstance toEntity(InstanceRequest request) {
        ProxyInstance instance = new ProxyInstance();
        instance.name = request.name;
        instance.slug = request.slug;
        instance.profileId = request.profileId;
        instance.pluginGroupId = request.pluginGroupId;
        instance.defaultModel = request.defaultModel;
        instance.enabled = request.enabled;
        return instance;
    }

    public record InstanceRequest(String name, String slug, Long profileId, Long pluginGroupId,
                                  String defaultModel, boolean enabled) {
    }
}
