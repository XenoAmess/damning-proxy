package com.xenoamess.damning_proxy.repository.panache;

import com.xenoamess.damning_proxy.entity.GlobalSettings;
import com.xenoamess.damning_proxy.repository.GlobalSettingsRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class PanacheGlobalSettingsRepository implements GlobalSettingsRepository {

    private static final long CACHE_TTL_MILLIS = 5_000;

    private volatile CachedSettings cached;

    @Override
    @Transactional
    public GlobalSettings save(GlobalSettings settings) {
        try {
            if (settings.id == null) {
                settings.persistAndFlush();
                return settings;
            }
            return PanacheUtils.saveOrUpdate(settings, findById(settings.id).orElse(null),
                (from, to) -> {
                    to.maxRequestsPerWindow = from.maxRequestsPerWindow;
                    to.windowSeconds = from.windowSeconds;
                });
        } finally {
            invalidateCache();
        }
    }

    @Override
    public Optional<GlobalSettings> findById(Long id) {
        return GlobalSettings.findByIdOptional(id);
    }

    @Override
    @Transactional
    public GlobalSettings getOrCreateSingleton() {
        long now = System.currentTimeMillis();
        CachedSettings snapshot = cached;
        if (snapshot != null && now - snapshot.timestamp < CACHE_TTL_MILLIS) {
            return snapshot.settings;
        }
        Optional<GlobalSettings> existing = GlobalSettings.findAll(Sort.by("id")).firstResultOptional();
        if (existing.isPresent()) {
            GlobalSettings settings = existing.get();
            cached = new CachedSettings(settings, now);
            return settings;
        }
        synchronized (this) {
            existing = GlobalSettings.findAll(Sort.by("id")).firstResultOptional();
            if (existing.isPresent()) {
                GlobalSettings settings = existing.get();
                cached = new CachedSettings(settings, now);
                return settings;
            }
            GlobalSettings settings = GlobalSettings.defaults();
            settings.persistAndFlush();
            cached = new CachedSettings(settings, now);
            return settings;
        }
    }

    @Override
    public void invalidateCache() {
        cached = null;
    }

    private record CachedSettings(GlobalSettings settings, long timestamp) {
    }
}
