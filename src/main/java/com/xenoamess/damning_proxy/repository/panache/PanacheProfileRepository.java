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
            return profile;
        }
        return PanacheUtils.saveOrUpdate(profile, findById(profile.id).orElse(null),
            (from, to) -> {
                to.name = from.name;
                to.slug = from.slug;
                to.baseUrl = from.baseUrl;
                to.bearerToken = from.bearerToken;
                to.customHeaders = from.customHeaders;
                to.customBody = from.customBody;
                to.defaultModel = from.defaultModel;
                to.timeoutMs = from.timeoutMs;
                to.circuitBreakerFailureThreshold = from.circuitBreakerFailureThreshold;
                to.circuitBreakerOpenTimeoutSeconds = from.circuitBreakerOpenTimeoutSeconds;
                to.enabled = from.enabled;
            });
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
