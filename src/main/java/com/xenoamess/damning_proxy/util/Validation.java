package com.xenoamess.damning_proxy.util;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public final class Validation {

    private Validation() {
    }

    public static void validateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new WebApplicationException("slug is required", Response.Status.BAD_REQUEST);
        }
    }
}
