package com.xenoamess.damning_proxy.api.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.repository.PluginRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/plugins")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PluginAdminApi {

    @Inject
    PluginRepository pluginRepository;

    @Inject
    ObjectMapper objectMapper;

    @GET
    public List<Plugin> list() {
        return pluginRepository.listAll();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        return pluginRepository.findById(id)
            .map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Transactional
    public Response create(Plugin plugin) {
        plugin.sample = false;
        pluginRepository.save(plugin);
        return Response.status(Response.Status.CREATED).entity(plugin).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, Plugin plugin) {
        return pluginRepository.findById(id)
            .map(existing -> {
                plugin.id = id;
                plugin.sample = false;
                pluginRepository.save(plugin);
                return Response.ok(plugin).build();
            })
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = pluginRepository.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    @POST
    @Path("/export")
    @Produces(MediaType.APPLICATION_JSON)
    public Response export(ExportRequest request) {
        List<Plugin> plugins;
        if (request != null && request.ids != null && !request.ids.isEmpty()) {
            plugins = request.ids.stream()
                .map(id -> pluginRepository.findById(id).orElse(null))
                .filter(p -> p != null)
                .collect(Collectors.toList());
        } else {
            plugins = pluginRepository.listAll();
        }
        List<ExportPlugin> exportPlugins = plugins.stream()
            .map(p -> new ExportPlugin(p.name, p.description, p.language.name(), p.executionPhase.name(), p.script, p.enabled))
            .collect(Collectors.toList());
        return Response.ok(exportPlugins).build();
    }

    @POST
    @Path("/import")
    @Transactional
    public Response importPlugins(List<ExportPlugin> plugins) {
        if (plugins == null || plugins.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("No plugins to import").build();
        }
        int imported = 0;
        int skipped = 0;
        for (ExportPlugin ep : plugins) {
            if (ep.script == null || ep.script.isBlank()) {
                continue;
            }
            List<Plugin> existing = pluginRepository.findByScript(ep.script);
            if (!existing.isEmpty()) {
                skipped++;
                continue;
            }
            Plugin plugin = new Plugin();
            plugin.name = ep.name;
            plugin.description = ep.description;
            plugin.language = Plugin.Language.valueOf(ep.language);
            plugin.executionPhase = Plugin.ExecutionPhase.valueOf(ep.executionPhase);
            plugin.script = ep.script;
            plugin.enabled = ep.enabled;
            plugin.sample = false;
            pluginRepository.save(plugin);
            imported++;
        }
        return Response.ok(new ImportResult(imported, skipped)).build();
    }

    public record ExportRequest(List<Long> ids) {
    }

    public record ExportPlugin(String name, String description, String language, String executionPhase, String script, boolean enabled) {
    }

    public record ImportResult(int imported, int skipped) {
    }
}
