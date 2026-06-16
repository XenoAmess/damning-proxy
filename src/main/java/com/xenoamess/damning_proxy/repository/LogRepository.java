package com.xenoamess.damning_proxy.repository;

import com.xenoamess.damning_proxy.entity.TrafficLog;

import java.util.List;
import java.util.Optional;

public interface LogRepository {

    TrafficLog save(TrafficLog log);

    Optional<TrafficLog> findById(Long id);

    List<TrafficLog> listRecent(int limit);

    List<TrafficLog> findByProfileId(Long profileId, int limit);

    boolean deleteById(Long id);

    long deleteAll();
}
