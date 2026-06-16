package com.xenoamess.damning_proxy.repository;

import com.xenoamess.damning_proxy.entity.PluginGroup;

import java.util.List;
import java.util.Optional;

public interface PluginGroupRepository {

    PluginGroup save(PluginGroup group);

    Optional<PluginGroup> findById(Long id);

    Optional<PluginGroup> findBySlug(String slug);

    List<PluginGroup> listAll();

    boolean deleteById(Long id);
}
