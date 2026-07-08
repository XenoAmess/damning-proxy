package com.xenoamess.damning_proxy.api.admin;

import com.xenoamess.damning_proxy.service.MetricsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;

@Path("/api/metrics")
@Produces(MediaType.APPLICATION_JSON)
public class MetricsAdminApi {

    @Inject
    MetricsService metricsService;

    @GET
    @Path("/summary")
    public Response summary(
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime) {
        TimeRange range = resolveRange(startTime, endTime);
        return Response.ok(metricsService.summary(range.start(), range.end())).build();
    }

    @GET
    @Path("/time-series")
    public Response timeSeries(
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime,
            @QueryParam("bucketMinutes") @DefaultValue("60") int bucketMinutes) {
        TimeRange range = resolveRange(startTime, endTime);
        return Response.ok(metricsService.timeSeries(range.start(), range.end(), bucketMinutes)).build();
    }

    @GET
    @Path("/top-instances")
    public Response topInstances(
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime,
            @QueryParam("limit") @DefaultValue("10") int limit) {
        TimeRange range = resolveRange(startTime, endTime);
        return Response.ok(metricsService.topInstances(range.start(), range.end(), limit)).build();
    }

    @GET
    @Path("/status-distribution")
    public Response statusDistribution(
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime) {
        TimeRange range = resolveRange(startTime, endTime);
        return Response.ok(metricsService.statusDistribution(range.start(), range.end())).build();
    }

    private TimeRange resolveRange(String startTime, String endTime) {
        LocalDateTime end = parseDateTime(endTime);
        if (end == null) {
            end = LocalDateTime.now();
        }
        LocalDateTime start = parseDateTime(startTime);
        if (start == null) {
            start = end.minusDays(1);
        }
        return new TimeRange(start, end);
    }

    private record TimeRange(LocalDateTime start, LocalDateTime end) {
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
