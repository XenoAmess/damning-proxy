package com.xenoamess.daming_proxy.filter;

import io.quarkus.logging.Log;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

@Provider
public class BearerTokenFilter implements ContainerRequestFilter {

    // You can configure this via application.properties
    private static final String EXPECTED_TOKEN = System.getenv().getOrDefault("API_TOKEN", "sk-daming-proxy-demo-token");

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        // Skip auth for health endpoint
        if (path.endsWith("/health")) {
            return;
        }

        String authHeader = requestContext.getHeaderString("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Log.warnf("Missing or invalid Authorization header for path: %s", path);
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorBody("Missing or invalid Authorization header. Expected: Bearer <token>"))
                    .build()
            );
            return;
        }

        String token = authHeader.substring(7);

        if (!EXPECTED_TOKEN.equals(token)) {
            Log.warnf("Invalid token provided for path: %s", path);
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorBody("Invalid API token"))
                    .build()
            );
        }
    }

    public static class ErrorBody {
        public String error;
        public ErrorBody(String error) { this.error = error; }
    }
}
