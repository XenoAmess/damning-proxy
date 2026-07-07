package com.xenoamess.damning_proxy.repository;

import com.xenoamess.damning_proxy.entity.PluginScriptRevision;

import java.util.List;
import java.util.Optional;

public interface PluginScriptRevisionRepository {

    PluginScriptRevision save(PluginScriptRevision revision);

    Optional<PluginScriptRevision> findById(Long id);

    List<PluginScriptRevision> findByPluginId(Long pluginId);

    boolean deleteById(Long id);

    void deleteByPluginId(Long pluginId);
}
