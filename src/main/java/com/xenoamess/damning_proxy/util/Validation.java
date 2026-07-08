package com.xenoamess.damning_proxy.util;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.regex.Pattern;

public final class Validation {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private Validation() {
    }

    public static void validateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new WebApplicationException("slug is required", Response.Status.BAD_REQUEST);
        }
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new WebApplicationException("slug must only contain letters, digits, underscores, or hyphens", Response.Status.BAD_REQUEST);
        }
    }

    public static void validatePathName(String name) {
        if (name == null || name.isBlank()) {
            throw new WebApplicationException("Name is required", Response.Status.BAD_REQUEST);
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new WebApplicationException("Name must not contain path separators", Response.Status.BAD_REQUEST);
        }
    }
}
