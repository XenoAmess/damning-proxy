package com.xenoamess.damning_proxy.repository;

import com.xenoamess.damning_proxy.entity.TrafficLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LogRepository {

    TrafficLog save(TrafficLog log);

    Optional<TrafficLog> findById(Long id);

    List<TrafficLog> listRecent(int limit);

    List<TrafficLog> findByProfileId(Long profileId, int limit);

    List<TrafficLog> findByInstanceId(Long instanceId, int limit);

    List<TrafficLog> findByFilters(Long instanceId, Long profileId, String status, String path,
                                   LocalDateTime startTime, LocalDateTime endTime, int offset, int limit);

    long count();

    long countByProfileId(Long profileId);

    long countByInstanceId(Long instanceId);

    long countByFilters(Long instanceId, Long profileId, String status, String path,
                        LocalDateTime startTime, LocalDateTime endTime);

    boolean deleteById(Long id);

    long deleteAll();

    void deleteOldest(int count);

    default void deleteOldest(int count, int batchSize) {
        deleteOldest(count);
    }

    long deleteByFilters(Long instanceId, Long profileId, String status, String path,
                         LocalDateTime startTime, LocalDateTime endTime);

    long deleteOlderThan(LocalDateTime cutoff);
}
