package com.xenoamess.damning_proxy.repository.panache;

import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheProfileRepository implements ProfileRepository {

    @Override
    public ProxyProfile save(ProxyProfile profile) {
        if (profile.id == null) {
            profile.persistAndFlush();
        } else {
            ProxyProfile existing = findById(profile.id).orElse(null);
            if (existing == null) {
                profile.persistAndFlush();
            } else {
                existing.name = profile.name;
                existing.slug = profile.slug;
                existing.baseUrl = profile.baseUrl;
                existing.bearerToken = profile.bearerToken;
                existing.customHeaders = profile.customHeaders;
                existing.defaultModel = profile.defaultModel;
                existing.timeoutMs = profile.timeoutMs;
                existing.enabled = profile.enabled;
                existing.persistAndFlush();
            }
        }
        return profile;
    }

    @Override
    public Optional<ProxyProfile> findById(Long id) {
        return ProxyProfile.findByIdOptional(id);
    }

    @Override
    public Optional<ProxyProfile> findBySlug(String slug) {
        return ProxyProfile.find("slug", slug).firstResultOptional();
    }

    @Override
    public List<ProxyProfile> listAll() {
        return ProxyProfile.listAll();
    }

    @Override
    public boolean deleteById(Long id) {
        return ProxyProfile.deleteById(id);
    }
}
