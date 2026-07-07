package com.xenoamess.damning_proxy.repository.panache;

import com.xenoamess.damning_proxy.entity.GlobalSettings;
import com.xenoamess.damning_proxy.repository.GlobalSettingsRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class PanacheGlobalSettingsRepository implements GlobalSettingsRepository {

    @Override
    @Transactional
    public GlobalSettings save(GlobalSettings settings) {
        if (settings.id == null) {
            settings.persistAndFlush();
            return settings;
        }
        return PanacheUtils.saveOrUpdate(settings, findById(settings.id).orElse(null),
            (from, to) -> {
                to.maxRequestsPerWindow = from.maxRequestsPerWindow;
                to.windowSeconds = from.windowSeconds;
            });
    }

    @Override
    public Optional<GlobalSettings> findById(Long id) {
        return GlobalSettings.findByIdOptional(id);
    }

    @Override
    @Transactional
    public GlobalSettings getOrCreateSingleton() {
        Optional<GlobalSettings> existing = GlobalSettings.findAll(Sort.by("id")).firstResultOptional();
        if (existing.isPresent()) {
            return existing.get();
        }
        GlobalSettings settings = GlobalSettings.defaults();
        settings.persistAndFlush();
        return settings;
    }
}
