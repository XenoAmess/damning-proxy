package com.xenoamess.damning_proxy.repository.panache;

import com.xenoamess.damning_proxy.entity.PluginScriptRevision;
import com.xenoamess.damning_proxy.repository.PluginScriptRevisionRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanachePluginScriptRevisionRepository implements PluginScriptRevisionRepository {

    @Override
    public PluginScriptRevision save(PluginScriptRevision revision) {
        if (revision.id == null) {
            revision.persistAndFlush();
            return revision;
        }
        return PanacheUtils.saveOrUpdate(revision, findById(revision.id).orElse(null),
            (from, to) -> {
                to.pluginId = from.pluginId;
                to.script = from.script;
                to.createdBy = from.createdBy;
            });
    }

    @Override
    public Optional<PluginScriptRevision> findById(Long id) {
        return PluginScriptRevision.findByIdOptional(id);
    }

    @Override
    public List<PluginScriptRevision> findByPluginId(Long pluginId) {
        return PluginScriptRevision.find("pluginId", Sort.by("createdAt").descending(), pluginId).list();
    }

    @Override
    public boolean deleteById(Long id) {
        return PluginScriptRevision.deleteById(id);
    }

    @Override
    public void deleteByPluginId(Long pluginId) {
        PluginScriptRevision.delete("pluginId", pluginId);
    }
}
