package com.xenoamess.damning_proxy.filter;

import io.quarkus.logging.Log;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        Log.error("Unhandled exception", exception);

        if (exception instanceof WebApplicationException wae) {
            Response originalResponse = wae.getResponse();
            if (originalResponse.getStatus() == 400) {
                return Response.status(400)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of(
                        "error", Map.of(
                            "message", exception.getMessage(),
                            "type", "invalid_request_error"
                        )
                    ))
                    .build();
            }
            return originalResponse;
        }

        return Response.status(500)
            .type(MediaType.APPLICATION_JSON)
            .entity(Map.of(
                "error", Map.of(
                    "message", "Internal server error: " + exception.getMessage(),
                    "type", "internal_error"
                )
            ))
            .build();
    }
}
