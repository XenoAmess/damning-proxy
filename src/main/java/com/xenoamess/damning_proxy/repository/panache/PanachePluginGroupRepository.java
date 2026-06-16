package com.xenoamess.damning_proxy.repository.panache;

import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.entity.PluginGroup;
import com.xenoamess.damning_proxy.entity.PluginGroupItem;
import com.xenoamess.damning_proxy.repository.PluginGroupRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanachePluginGroupRepository implements PluginGroupRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    public PluginGroup save(PluginGroup group) {
        if (group.id == null) {
            attachItems(group);
            group.persistAndFlush();
            return group;
        }

        PluginGroup existing = findById(group.id).orElse(null);
        if (existing == null) {
            attachItems(group);
            group.persistAndFlush();
            return group;
        }

        existing.name = group.name;
        existing.slug = group.slug;
        existing.description = group.description;
        existing.enabled = group.enabled;

        existing.items.clear();
        for (PluginGroupItem item : group.items) {
            PluginGroupItem managed = new PluginGroupItem();
            managed.group = existing;
            managed.plugin = em.getReference(Plugin.class, item.plugin.id);
            managed.orderIndex = item.orderIndex != null ? item.orderIndex : 0;
            managed.priority = item.priority != null ? item.priority : 0;
            managed.enabled = item.enabled;
            existing.items.add(managed);
        }

        existing.persistAndFlush();
        return existing;
    }

    private void attachItems(PluginGroup group) {
        List<PluginGroupItem> attached = new ArrayList<>();
        for (PluginGroupItem item : group.items) {
            item.group = group;
            if (item.plugin != null && item.plugin.id != null) {
                item.plugin = em.getReference(Plugin.class, item.plugin.id);
            }
            attached.add(item);
        }
        group.items = attached;
    }

    @Override
    public Optional<PluginGroup> findById(Long id) {
        return PluginGroup.findByIdOptional(id);
    }

    @Override
    public Optional<PluginGroup> findBySlug(String slug) {
        return PluginGroup.find("slug", slug).firstResultOptional();
    }

    @Override
    public List<PluginGroup> listAll() {
        return PluginGroup.listAll();
    }

    @Override
    public boolean deleteById(Long id) {
        return PluginGroup.deleteById(id);
    }
}
