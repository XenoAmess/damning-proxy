package com.xenoamess.damning_proxy.api.admin;

import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.entity.PluginGroup;
import com.xenoamess.damning_proxy.entity.PluginGroupItem;
import com.xenoamess.damning_proxy.repository.PluginGroupRepository;
import com.xenoamess.damning_proxy.repository.PluginRepository;
import com.xenoamess.damning_proxy.util.Validation;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        Validation.validateSlug(request.slug);
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
        Validation.validateSlug(request.slug);
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

    @POST
    @Path("/export")
    @Produces(MediaType.APPLICATION_JSON)
    public Response export(ExportRequest request) {
        List<PluginGroup> groups;
        if (request != null && request.ids != null && !request.ids.isEmpty()) {
            groups = request.ids.stream()
                .map(id -> groupRepository.findById(id).orElse(null))
                .filter(g -> g != null)
                .collect(Collectors.toList());
        } else {
            groups = groupRepository.listAll();
        }
        List<ExportGroup> exportGroups = groups.stream()
            .map(g -> new ExportGroup(
                g.name,
                g.slug,
                g.description,
                g.enabled,
                g.sortedItems().stream()
                    .filter(i -> i.plugin != null && i.plugin.script != null)
                    .map(i -> new ExportItem(i.plugin.script, i.orderIndex, i.priority, i.enabled))
                    .collect(Collectors.toList())))
            .collect(Collectors.toList());
        return Response.ok(exportGroups).build();
    }

    @POST
    @Path("/import")
    @Transactional
    public Response importGroups(List<ExportGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("No plugin groups to import").build();
        }
        int imported = 0;
        int skipped = 0;
        for (ExportGroup eg : groups) {
            if (eg.slug == null || eg.slug.isBlank()) {
                continue;
            }
            if (groupRepository.findBySlug(eg.slug).isPresent()) {
                skipped++;
                continue;
            }
            PluginGroup group = new PluginGroup();
            group.name = eg.name;
            group.slug = eg.slug;
            group.description = eg.description;
            group.enabled = eg.enabled;
            group.items = new ArrayList<>();
            if (eg.items != null) {
                for (ExportItem ei : eg.items) {
                    if (ei.pluginScript == null || ei.pluginScript.isBlank()) {
                        continue;
                    }
                    List<Plugin> matched = pluginRepository.findByScript(ei.pluginScript);
                    if (matched.isEmpty()) {
                        continue;
                    }
                    PluginGroupItem item = new PluginGroupItem();
                    item.plugin = matched.get(0);
                    item.orderIndex = ei.orderIndex != null ? ei.orderIndex : 0;
                    item.priority = ei.priority != null ? ei.priority : 0;
                    item.enabled = ei.enabled;
                    group.items.add(item);
                }
            }
            groupRepository.save(group);
            imported++;
        }
        return Response.ok(new ImportResult(imported, skipped)).build();
    }

    public record ExportRequest(List<Long> ids) {
    }

    public record ExportGroup(String name, String slug, String description, boolean enabled, List<ExportItem> items) {
    }

    public record ExportItem(String pluginScript, Integer orderIndex, Integer priority, boolean enabled) {
    }

    public record ImportResult(int imported, int skipped) {
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
