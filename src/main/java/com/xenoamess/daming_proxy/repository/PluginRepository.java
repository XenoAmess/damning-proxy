package com.xenoamess.daming_proxy.repository;

import com.xenoamess.daming_proxy.entity.Plugin;

import java.util.List;
import java.util.Optional;

public interface PluginRepository {

    Plugin save(Plugin plugin);

    Optional<Plugin> findById(Long id);

    List<Plugin> listAll();

    List<Plugin> findEnabledByProfileId(Long profileId);

    List<Plugin> findEnabledGlobal();

    boolean deleteById(Long id);
}
