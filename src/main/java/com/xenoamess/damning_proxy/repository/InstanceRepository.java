package com.xenoamess.damning_proxy.repository;

import com.xenoamess.damning_proxy.entity.ProxyInstance;

import java.util.List;
import java.util.Optional;

public interface InstanceRepository {

    ProxyInstance save(ProxyInstance instance);

    Optional<ProxyInstance> findById(Long id);

    Optional<ProxyInstance> findBySlug(String slug);

    List<ProxyInstance> listAll();

    boolean deleteById(Long id);
}
