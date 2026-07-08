package com.xenoamess.damning_proxy.filter;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionMapperTest {

    private final GlobalExceptionMapper mapper = new GlobalExceptionMapper();

    @Test
    void shouldMapWae400ToOpenAiError() {
        WebApplicationException wae = new WebApplicationException(
            "invalid slug",
            Response.status(400).build()
        );

        Response response = mapper.toResponse(wae);

        assertEquals(400, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE.getType(), response.getMediaType().getType());
        assertTrue(response.getEntity() instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertTrue(body.containsKey("error"));
    }

    @Test
    void shouldPassThroughWaeWithStatusOtherThan400() {
        Response original = Response.status(503).entity("service unavailable").build();
        WebApplicationException wae = new WebApplicationException("downstream failure", original);

        Response response = mapper.toResponse(wae);

        assertEquals(503, response.getStatus());
        assertEquals("service unavailable", response.getEntity());
    }

    @Test
    void shouldMapGenericExceptionTo500() {
        RuntimeException ex = new RuntimeException("something broke");

        Response response = mapper.toResponse(ex);

        assertEquals(500, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE.getType(), response.getMediaType().getType());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertEquals("internal_error", error.get("type"));
        assertTrue(((String) error.get("message")).contains("something broke"));
    }

    @Test
    void shouldMapWaeWithNullMessage() {
        WebApplicationException wae = new WebApplicationException(
            Response.status(400).build()
        );

        Response response = mapper.toResponse(wae);

        assertEquals(400, response.getStatus());
    }
}
