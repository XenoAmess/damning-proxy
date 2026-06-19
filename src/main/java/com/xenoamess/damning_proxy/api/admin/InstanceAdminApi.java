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
import java.util.stream.Collectors;

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

    @POST
    @Path("/export")
    @Produces(MediaType.APPLICATION_JSON)
    public Response export(ExportRequest request) {
        List<ProxyInstance> instances;
        if (request != null && request.ids != null && !request.ids.isEmpty()) {
            instances = request.ids.stream()
                .map(id -> instanceRepository.findById(id).orElse(null))
                .filter(i -> i != null)
                .collect(Collectors.toList());
        } else {
            instances = instanceRepository.listAll();
        }
        List<ExportInstance> exportInstances = instances.stream()
            .map(i -> new ExportInstance(
                i.name,
                i.slug,
                profileRepository.findById(i.profileId).map(p -> p.slug).orElse(null),
                pluginGroupRepository.findById(i.pluginGroupId).map(g -> g.slug).orElse(null),
                i.defaultModel,
                i.enabled))
            .collect(Collectors.toList());
        return Response.ok(exportInstances).build();
    }

    @POST
    @Path("/import")
    @Transactional
    public Response importInstances(List<ExportInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("No instances to import").build();
        }
        int imported = 0;
        int skipped = 0;
        for (ExportInstance ei : instances) {
            if (ei.slug == null || ei.slug.isBlank()) {
                continue;
            }
            if (instanceRepository.findBySlug(ei.slug).isPresent()) {
                skipped++;
                continue;
            }
            Long profileId = profileRepository.findBySlug(ei.profileSlug).map(p -> p.id).orElse(null);
            Long pluginGroupId = pluginGroupRepository.findBySlug(ei.pluginGroupSlug).map(g -> g.id).orElse(null);
            if (profileId == null || pluginGroupId == null) {
                continue;
            }
            ProxyInstance instance = new ProxyInstance();
            instance.name = ei.name;
            instance.slug = ei.slug;
            instance.profileId = profileId;
            instance.pluginGroupId = pluginGroupId;
            instance.defaultModel = ei.defaultModel;
            instance.enabled = ei.enabled;
            instanceRepository.save(instance);
            imported++;
        }
        return Response.ok(new ImportResult(imported, skipped)).build();
    }

    public record ExportRequest(List<Long> ids) {
    }

    public record ExportInstance(String name, String slug, String profileSlug, String pluginGroupSlug,
                                 String defaultModel, boolean enabled) {
    }

    public record ImportResult(int imported, int skipped) {
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
        instance.defaultModel = resolveDefaultModel(request);
        instance.enabled = request.enabled;
        return instance;
    }

    private String resolveDefaultModel(InstanceRequest request) {
        if (request.defaultModel != null && !request.defaultModel.isBlank()) {
            return request.defaultModel;
        }
        return profileRepository.findById(request.profileId)
            .map(p -> p.defaultModel)
            .orElse(null);
    }

    public record InstanceRequest(String name, String slug, Long profileId, Long pluginGroupId,
                                  String defaultModel, boolean enabled) {
    }
}
