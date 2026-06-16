package com.xenoamess.daming_proxy.api.admin;

import com.xenoamess.daming_proxy.entity.Plugin;
import com.xenoamess.daming_proxy.repository.PluginRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/plugins")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PluginAdminApi {

    @Inject
    PluginRepository pluginRepository;

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
}
