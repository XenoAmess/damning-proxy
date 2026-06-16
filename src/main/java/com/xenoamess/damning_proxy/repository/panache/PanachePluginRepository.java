package com.xenoamess.damning_proxy.repository.panache;

import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.repository.PluginRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanachePluginRepository implements PluginRepository {

    @Override
    public Plugin save(Plugin plugin) {
        if (plugin.id == null) {
            plugin.persistAndFlush();
        } else {
            Plugin existing = findById(plugin.id).orElse(null);
            if (existing == null) {
                plugin.persistAndFlush();
            } else {
                existing.name = plugin.name;
                existing.description = plugin.description;
                existing.language = plugin.language;
                existing.script = plugin.script;
                existing.executionPhase = plugin.executionPhase;
                existing.enabled = plugin.enabled;
                existing.persistAndFlush();
            }
        }
        return plugin;
    }

    @Override
    public Optional<Plugin> findById(Long id) {
        return Plugin.findByIdOptional(id);
    }

    @Override
    public List<Plugin> listAll() {
        return Plugin.listAll(Sort.by("name"));
    }

    @Override
    public long count() {
        return Plugin.count();
    }

    @Override
    public boolean deleteById(Long id) {
        return Plugin.deleteById(id);
    }
}
