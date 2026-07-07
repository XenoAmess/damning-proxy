package com.xenoamess.damning_proxy.repository.panache;

import com.xenoamess.damning_proxy.entity.TrafficLog;
import com.xenoamess.damning_proxy.repository.LogRepository;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheLogRepository implements LogRepository {

    @Override
    public TrafficLog save(TrafficLog log) {
        log.persist();
        return log;
    }

    @Override
    public Optional<TrafficLog> findById(Long id) {
        return TrafficLog.findByIdOptional(id);
    }

    @Override
    public List<TrafficLog> listRecent(int limit) {
        return TrafficLog.findAll(Sort.descending("requestTime")).page(0, limit).list();
    }

    @Override
    public List<TrafficLog> findByProfileId(Long profileId, int limit) {
        return TrafficLog.find("profileId", Sort.descending("requestTime"), profileId)
            .page(0, limit)
            .list();
    }

    @Override
    public List<TrafficLog> findByInstanceId(Long instanceId, int limit) {
        return TrafficLog.find("instanceId", Sort.descending("requestTime"), instanceId)
            .page(0, limit)
            .list();
    }

    @Override
    public List<TrafficLog> findByFilters(Long instanceId, Long profileId, String status, String path,
                                           LocalDateTime startTime, LocalDateTime endTime, int offset, int limit) {
        FilterQuery fq = buildFilterQuery(instanceId, profileId, status, path, startTime, endTime);
        return TrafficLog.find(fq.query, Sort.descending("requestTime"), fq.params)
            .page(offset / limit, limit)
            .list();
    }

    @Override
    public long count() {
        return TrafficLog.count();
    }

    @Override
    public long countByProfileId(Long profileId) {
        return TrafficLog.count("profileId", profileId);
    }

    @Override
    public long countByInstanceId(Long instanceId) {
        return TrafficLog.count("instanceId", instanceId);
    }

    @Override
    public long countByFilters(Long instanceId, Long profileId, String status, String path,
                               LocalDateTime startTime, LocalDateTime endTime) {
        FilterQuery fq = buildFilterQuery(instanceId, profileId, status, path, startTime, endTime);
        return TrafficLog.count(fq.query, fq.params);
    }

    @Override
    public boolean deleteById(Long id) {
        return TrafficLog.deleteById(id);
    }

    @Override
    public long deleteAll() {
        return TrafficLog.deleteAll();
    }

    @Override
    public void deleteOldest(int count) {
        deleteOldest(count, 1000);
    }

    @Override
    public void deleteOldest(int count, int batchSize) {
        int effectiveBatchSize = Math.max(1, batchSize);
        int remaining = count;
        while (remaining > 0) {
            int limit = Math.min(effectiveBatchSize, remaining);
            List<TrafficLog> oldest = TrafficLog.findAll(Sort.ascending("requestTime")).page(0, limit).list();
            if (oldest.isEmpty()) {
                break;
            }
            for (TrafficLog log : oldest) {
                log.delete();
            }
            TrafficLog.flush();
            remaining -= oldest.size();
        }
    }

    @Override
    public long deleteByFilters(Long instanceId, Long profileId, String status, String path,
                                LocalDateTime startTime, LocalDateTime endTime) {
        FilterQuery fq = buildFilterQuery(instanceId, profileId, status, path, startTime, endTime);
        return TrafficLog.delete(fq.query, fq.params);
    }

    @Override
    public long deleteOlderThan(LocalDateTime cutoff) {
        long deleted = 0;
        List<TrafficLog> all = TrafficLog.listAll();
        for (TrafficLog log : all) {
            if (log.requestTime != null && log.requestTime.isBefore(cutoff)) {
                log.delete();
                deleted++;
            }
        }
        return deleted;
    }

    private record FilterQuery(String query, Parameters params) {
    }

    private FilterQuery buildFilterQuery(Long instanceId, Long profileId, String status, String path,
                                         LocalDateTime startTime, LocalDateTime endTime) {
        List<String> clauses = new ArrayList<>();
        Parameters params = new Parameters();
        if (instanceId != null) {
            clauses.add("instanceId = :instanceId");
            params = params.and("instanceId", instanceId);
        }
        if (profileId != null) {
            clauses.add("profileId = :profileId");
            params = params.and("profileId", profileId);
        }
        if ("success".equalsIgnoreCase(status)) {
            clauses.add("(responseStatus < 400 or responseStatus is null)");
        } else if ("error".equalsIgnoreCase(status)) {
            clauses.add("responseStatus >= 400");
        }
        if (path != null && !path.isBlank()) {
            clauses.add("requestPath like :path");
            params = params.and("path", "%" + path + "%");
        }
        if (startTime != null) {
            clauses.add("requestTime >= :startTime");
            params = params.and("startTime", startTime);
        }
        if (endTime != null) {
            clauses.add("requestTime <= :endTime");
            params = params.and("endTime", endTime);
        }
        String query = clauses.isEmpty() ? "1=1" : String.join(" and ", clauses);
        return new FilterQuery(query, params);
    }
}
