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
            return plugin;
        }
        return PanacheUtils.saveOrUpdate(plugin, findById(plugin.id).orElse(null),
            (from, to) -> {
                to.name = from.name;
                to.slug = from.slug;
                to.description = from.description;
                to.language = from.language;
                to.mode = from.mode;
                to.script = from.script;
                to.packagePath = from.packagePath;
                to.executionPhase = from.executionPhase;
                to.enabled = from.enabled;
                to.sample = from.sample;
            });
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
    public Optional<Plugin> findBySlug(String slug) {
        return Plugin.find("slug", slug).firstResultOptional();
    }

    @Override
    public List<Plugin> findByScript(String script) {
        return Plugin.find("script", script).list();
    }

    @Override
    public Optional<Plugin> findSampleByScript(String script) {
        return Plugin.find("sample = true and script = ?1", script).firstResultOptional();
    }

    @Override
    public boolean deleteById(Long id) {
        return Plugin.deleteById(id);
    }
}
