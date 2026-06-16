package com.xenoamess.damning_proxy.api.admin;

import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.entity.PluginGroup;
import com.xenoamess.damning_proxy.entity.PluginGroupItem;
import com.xenoamess.damning_proxy.repository.PluginGroupRepository;
import com.xenoamess.damning_proxy.repository.PluginRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Path("/api/plugin-groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PluginGroupAdminApi {

    @Inject
    PluginGroupRepository groupRepository;

    @Inject
    PluginRepository pluginRepository;

    @GET
    public List<PluginGroup> list() {
        return groupRepository.listAll();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        return groupRepository.findById(id)
            .map(g -> Response.ok(g).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Transactional
    public Response create(PluginGroupRequest request) {
        if (request.slug == null || request.slug.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("slug is required").build();
        }
        if (groupRepository.findBySlug(request.slug).isPresent()) {
            return Response.status(Response.Status.CONFLICT).entity("slug already exists").build();
        }
        PluginGroup group = toEntity(request);
        groupRepository.save(group);
        return Response.status(Response.Status.CREATED).entity(group).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, PluginGroupRequest request) {
        Optional<PluginGroup> existingOpt = groupRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (request.slug == null || request.slug.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("slug is required").build();
        }
        Optional<PluginGroup> bySlug = groupRepository.findBySlug(request.slug);
        if (bySlug.isPresent() && !bySlug.get().id.equals(id)) {
            return Response.status(Response.Status.CONFLICT).entity("slug already exists").build();
        }
        PluginGroup group = toEntity(request);
        group.id = id;
        groupRepository.save(group);
        return Response.ok(group).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = groupRepository.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    private PluginGroup toEntity(PluginGroupRequest request) {
        PluginGroup group = new PluginGroup();
        group.name = request.name;
        group.slug = request.slug;
        group.description = request.description;
        group.enabled = request.enabled;
        group.items = new ArrayList<>();
        if (request.items != null) {
            for (ItemRequest item : request.items) {
                PluginGroupItem gItem = new PluginGroupItem();
                gItem.plugin = pluginRepository.findById(item.pluginId).orElse(null);
                if (gItem.plugin == null) {
                    continue;
                }
                gItem.orderIndex = item.orderIndex != null ? item.orderIndex : 0;
                gItem.priority = item.priority != null ? item.priority : 0;
                gItem.enabled = item.enabled;
                group.items.add(gItem);
            }
        }
        return group;
    }

    public record PluginGroupRequest(String name, String slug, String description, boolean enabled, List<ItemRequest> items) {
    }

    public record ItemRequest(Long pluginId, Integer orderIndex, Integer priority, boolean enabled) {
    }
}
