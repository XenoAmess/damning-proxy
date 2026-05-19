package com.xenoamess.badass_model.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xenoamess.badass_model.dto.ErrorResponse;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public Response toResponse(Exception exception) {
        Log.error("Unhandled exception", exception);

        if (exception instanceof WebApplicationException wae) {
            Response originalResponse = wae.getResponse();
            if (originalResponse.getStatus() == 400) {
                return Response.status(400)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse(new ErrorResponse.Error(
                        exception.getMessage(), "invalid_request_error", null, null
                    )))
                    .build();
            }
            return originalResponse;
        }

        return Response.status(500)
            .type(MediaType.APPLICATION_JSON)
            .entity(new ErrorResponse(new ErrorResponse.Error(
                "Internal server error: " + exception.getMessage(),
                "internal_error",
                null,
                null
            )))
            .build();
    }
}
