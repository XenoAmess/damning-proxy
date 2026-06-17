package com.xenoamess.damning_proxy.repository;

import com.xenoamess.damning_proxy.entity.Plugin;

import java.util.List;
import java.util.Optional;

public interface PluginRepository {

    Plugin save(Plugin plugin);

    Optional<Plugin> findById(Long id);

    List<Plugin> listAll();

    long count();

    List<Plugin> findByScript(String script);

    Optional<Plugin> findSampleByScript(String script);

    boolean deleteById(Long id);
}
