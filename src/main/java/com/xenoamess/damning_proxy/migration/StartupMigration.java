package com.xenoamess.damning_proxy.migration;

import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.entity.PluginGroup;
import com.xenoamess.damning_proxy.entity.PluginGroupItem;
import com.xenoamess.damning_proxy.entity.ProxyInstance;
import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.repository.InstanceRepository;
import com.xenoamess.damning_proxy.repository.PluginGroupRepository;
import com.xenoamess.damning_proxy.repository.PluginRepository;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class StartupMigration {

    @Inject
    InstanceRepository instanceRepository;

    @Inject
    PluginGroupRepository pluginGroupRepository;

    @Inject
    PluginRepository pluginRepository;

    @Inject
    ProfileRepository profileRepository;

    @Transactional
    public void onStart(@Observes StartupEvent event) {
        if (!instanceRepository.listAll().isEmpty()) {
            return;
        }

        List<Plugin> allPlugins = pluginRepository.listAll();
        List<Plugin> globalPlugins = allPlugins.stream()
            .filter(p -> p.enabled)
            .sorted(Comparator.comparingInt((Plugin p) -> 0).thenComparingLong(p -> p.id))
            .toList();

        List<ProxyProfile> profiles = profileRepository.listAll();
        for (ProxyProfile profile : profiles) {
            List<Plugin> profilePlugins = allPlugins.stream()
                .filter(p -> p.enabled)
                .sorted(Comparator.comparingInt((Plugin p) -> 0).thenComparingLong(p -> p.id))
                .toList();

            PluginGroup group = new PluginGroup();
            group.name = "Default for " + profile.name;
            group.slug = "default-" + profile.slug;
            group.description = "Auto-migrated plugin group for profile " + profile.slug;
            group.enabled = true;
            group.items = new ArrayList<>();

            int order = 0;
            for (Plugin plugin : profilePlugins) {
                PluginGroupItem item = new PluginGroupItem();
                item.group = group;
                item.plugin = plugin;
                item.orderIndex = order++;
                item.priority = 0;
                item.enabled = true;
                group.items.add(item);
            }
            pluginGroupRepository.save(group);

            ProxyInstance instance = new ProxyInstance();
            instance.name = profile.name;
            instance.slug = profile.slug;
            instance.profileId = profile.id;
            instance.pluginGroupId = group.id;
            instance.defaultModel = profile.defaultModel;
            instance.enabled = profile.enabled;
            instanceRepository.save(instance);
        }
    }
}
