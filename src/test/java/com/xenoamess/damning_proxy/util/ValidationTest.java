package com.xenoamess.damning_proxy.util;

import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationTest {

    @Test
    void shouldAcceptValidSlugs() {
        assertDoesNotThrow(() -> Validation.validateSlug("my-instance"));
        assertDoesNotThrow(() -> Validation.validateSlug("instance_1"));
        assertDoesNotThrow(() -> Validation.validateSlug("Abc123"));
        assertDoesNotThrow(() -> Validation.validateSlug("a"));
        assertDoesNotThrow(() -> Validation.validateSlug("a-b_C9"));
    }

    @Test
    void shouldRejectNullSlug() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> Validation.validateSlug(null));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void shouldRejectEmptySlug() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> Validation.validateSlug(""));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void shouldRejectSlugWithSpaces() {
        assertThrows(WebApplicationException.class,
            () -> Validation.validateSlug("my instance"));
    }

    @Test
    void shouldRejectSlugWithSpecialChars() {
        assertThrows(WebApplicationException.class,
            () -> Validation.validateSlug("inst@nce"));
        assertThrows(WebApplicationException.class,
            () -> Validation.validateSlug("inst/nce"));
        assertThrows(WebApplicationException.class,
            () -> Validation.validateSlug("inst.nce"));
    }
}
