package com.xenoamess.damning_proxy.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
public class MetricsService {

    @Inject
    EntityManager entityManager;

    public Summary summary(LocalDateTime start, LocalDateTime end) {
        String countSql = "SELECT COUNT(*), COALESCE(AVG(duration_ms), 0), COALESCE(SUM(prompt_tokens), 0), COALESCE(SUM(completion_tokens), 0), COALESCE(SUM(total_tokens), 0) FROM traffic_log WHERE request_time >= :start AND request_time < :end";
        Query q = entityManager.createNativeQuery(countSql);
        q.setParameter("start", start);
        q.setParameter("end", end);
        Object[] row = (Object[]) q.getSingleResult();

        long total = ((Number) row[0]).longValue();
        double avgLatency = ((Number) row[1]).doubleValue();
        long promptTokens = ((Number) row[2]).longValue();
        long completionTokens = ((Number) row[3]).longValue();
        long totalTokens = ((Number) row[4]).longValue();

        String errorSql = "SELECT COUNT(*) FROM traffic_log WHERE request_time >= :start AND request_time < :end AND response_status >= 400";
        Query eq = entityManager.createNativeQuery(errorSql);
        eq.setParameter("start", start);
        eq.setParameter("end", end);
        long errors = ((Number) eq.getSingleResult()).longValue();

        return new Summary(total, errors, avgLatency, promptTokens, completionTokens, totalTokens);
    }

    @SuppressWarnings("unchecked")
    public List<TimeBucket> timeSeries(LocalDateTime start, LocalDateTime end, int bucketMinutes) {
        String jpql = "SELECT l.requestTime, l.durationMs, l.totalTokens, l.responseStatus FROM TrafficLog l WHERE l.requestTime >= :start AND l.requestTime < :end ORDER BY l.requestTime";
        Query q = entityManager.createQuery(jpql);
        q.setParameter("start", start);
        q.setParameter("end", end);
        List<Object[]> rows = q.getResultList();

        Map<String, BucketAccumulator> buckets = new TreeMap<>();
        for (Object[] row : rows) {
            LocalDateTime requestTime = (LocalDateTime) row[0];
            long durationMs = ((Number) row[1]).longValue();
            long totalTokens = ((Number) row[2]).longValue();
            int responseStatus = ((Number) row[3]).intValue();

            LocalDateTime bucketStart = toBucketStart(requestTime, bucketMinutes);
            String bucketKey = formatBucket(bucketStart, bucketMinutes);
            BucketAccumulator acc = buckets.computeIfAbsent(bucketKey, k -> new BucketAccumulator());
            acc.count++;
            acc.durationSum += durationMs;
            acc.tokens += totalTokens;
            if (responseStatus >= 400) {
                acc.errors++;
            }
        }

        List<TimeBucket> result = new ArrayList<>();
        for (Map.Entry<String, BucketAccumulator> e : buckets.entrySet()) {
            BucketAccumulator acc = e.getValue();
            double avgLatency = acc.count == 0 ? 0.0 : (double) acc.durationSum / acc.count;
            result.add(new TimeBucket(e.getKey(), acc.count, avgLatency, acc.tokens, acc.errors));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<InstanceMetrics> topInstances(LocalDateTime start, LocalDateTime end, int limit) {
        String sql = "SELECT instance_slug, COUNT(*) as cnt, COALESCE(AVG(duration_ms), 0) as avg_latency, " +
            "SUM(CASE WHEN response_status >= 400 THEN 1 ELSE 0 END) as errors " +
            "FROM traffic_log WHERE request_time >= :start AND request_time < :end " +
            "GROUP BY instance_slug ORDER BY cnt DESC LIMIT :limit";
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("start", start);
        q.setParameter("end", end);
        q.setParameter("limit", limit);
        List<Object[]> rows = q.getResultList();
        List<InstanceMetrics> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new InstanceMetrics(
                (String) row[0],
                ((Number) row[1]).longValue(),
                ((Number) row[2]).doubleValue(),
                ((Number) row[3]).longValue()
            ));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<StatusDistribution> statusDistribution(LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT CASE WHEN response_status >= 400 THEN 'error' ELSE 'success' END, COUNT(*) as cnt " +
            "FROM traffic_log WHERE request_time >= :start AND request_time < :end " +
            "GROUP BY CASE WHEN response_status >= 400 THEN 'error' ELSE 'success' END ORDER BY cnt DESC";
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("start", start);
        q.setParameter("end", end);
        List<Object[]> rows = q.getResultList();
        List<StatusDistribution> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new StatusDistribution((String) row[0], ((Number) row[1]).longValue()));
        }
        return result;
    }

    private static LocalDateTime toBucketStart(LocalDateTime dt, int bucketMinutes) {
        long bucketSeconds = Math.max(60L, (long) bucketMinutes * 60L);
        long epochSeconds = dt.toEpochSecond(ZoneOffset.UTC);
        long bucketStartSeconds = (epochSeconds / bucketSeconds) * bucketSeconds;
        return LocalDateTime.ofEpochSecond(bucketStartSeconds, 0, ZoneOffset.UTC);
    }

    private static String formatBucket(LocalDateTime bucket, int bucketMinutes) {
        if (bucketMinutes >= 60 * 24) {
            return bucket.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        if (bucketMinutes >= 60) {
            return bucket.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"));
        }
        return bucket.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private static class BucketAccumulator {
        long count;
        long durationSum;
        long tokens;
        long errors;
    }

    public record Summary(long totalRequests, long errorRequests, double avgLatencyMs,
                          long promptTokens, long completionTokens, long totalTokens) {
    }

    public record TimeBucket(String bucket, long requests, double avgLatencyMs, long tokens, long errors) {
    }

    public record InstanceMetrics(String instanceSlug, long requests, double avgLatencyMs, long errors) {
    }

    public record StatusDistribution(String status, long count) {
    }
}
