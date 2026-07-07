package com.xenoamess.damning_proxy.repository;

import com.xenoamess.damning_proxy.entity.GlobalSettings;

import java.util.Optional;

public interface GlobalSettingsRepository {

    GlobalSettings save(GlobalSettings settings);

    Optional<GlobalSettings> findById(Long id);

    GlobalSettings getOrCreateSingleton();
}
