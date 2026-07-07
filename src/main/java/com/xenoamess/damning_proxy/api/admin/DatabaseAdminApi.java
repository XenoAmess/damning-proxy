package com.xenoamess.damning_proxy.api.admin;

import com.xenoamess.damning_proxy.service.DatabaseAdminService;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/admin/database")
@Produces(MediaType.APPLICATION_JSON)
public class DatabaseAdminApi {

    @Inject
    DatabaseAdminService databaseAdminService;

    @POST
    @Path("/backup")
    public Response backup(@QueryParam("name") String name) {
        try {
            DatabaseAdminService.BackupResult result = databaseAdminService.backup(name);
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/restore")
    public Response restore(@QueryParam("path") String path) {
        if (path == null || path.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("path is required").build();
        }
        try {
            DatabaseAdminService.RestoreResult result = databaseAdminService.prepareRestore(path);
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }
}
