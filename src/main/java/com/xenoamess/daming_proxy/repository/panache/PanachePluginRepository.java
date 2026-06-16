package com.xenoamess.daming_proxy.repository.panache;

import com.xenoamess.daming_proxy.entity.Plugin;
import com.xenoamess.daming_proxy.repository.PluginRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanachePluginRepository implements PluginRepository {

    @Override
    public Plugin save(Plugin plugin) {
        if (plugin.id == null) {
            plugin.persist();
        } else {
            Plugin existing = findById(plugin.id).orElse(null);
            if (existing == null) {
                plugin.persist();
            } else {
                existing.name = plugin.name;
                existing.language = plugin.language;
                existing.script = plugin.script;
                existing.priority = plugin.priority;
                existing.executionPhase = plugin.executionPhase;
                existing.enabled = plugin.enabled;
                existing.globalScope = plugin.globalScope;
                existing.profileId = plugin.profileId;
                existing.persist();
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
        return Plugin.listAll(Sort.by("priority"));
    }

    @Override
    public List<Plugin> findEnabledByProfileId(Long profileId) {
        return Plugin.find(
            "enabled = true and globalScope = false and profileId = ?1",
            Sort.by("priority"),
            profileId
        ).list();
    }

    @Override
    public List<Plugin> findEnabledGlobal() {
        return Plugin.find("enabled = true and globalScope = true", Sort.by("priority")).list();
    }

    @Override
    public boolean deleteById(Long id) {
        return Plugin.deleteById(id);
    }
}
