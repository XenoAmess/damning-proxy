package com.xenoamess.damning_proxy.repository.panache;

import com.xenoamess.damning_proxy.entity.TrafficLog;
import com.xenoamess.damning_proxy.repository.LogRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

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
    public boolean deleteById(Long id) {
        return TrafficLog.deleteById(id);
    }

    @Override
    public long deleteAll() {
        return TrafficLog.deleteAll();
    }

    @Override
    public void deleteOldest(int count) {
        List<TrafficLog> oldest = TrafficLog.findAll(Sort.ascending("requestTime")).page(0, count).list();
        for (TrafficLog log : oldest) {
            log.delete();
        }
    }
}
