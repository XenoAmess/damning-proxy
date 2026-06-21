package com.xenoamess.damning_proxy.repository.panache;

import com.xenoamess.damning_proxy.entity.ProxyInstance;
import com.xenoamess.damning_proxy.repository.InstanceRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheInstanceRepository implements InstanceRepository {

    @Override
    public ProxyInstance save(ProxyInstance instance) {
        if (instance.id == null) {
            instance.persistAndFlush();
            return instance;
        }
        return PanacheUtils.saveOrUpdate(instance, findById(instance.id).orElse(null),
            (from, to) -> {
                to.name = from.name;
                to.slug = from.slug;
                to.profileId = from.profileId;
                to.pluginGroupId = from.pluginGroupId;
                to.defaultModel = from.defaultModel;
                to.enabled = from.enabled;
            });
    }

    @Override
    public Optional<ProxyInstance> findById(Long id) {
        return ProxyInstance.findByIdOptional(id);
    }

    @Override
    public Optional<ProxyInstance> findBySlug(String slug) {
        return ProxyInstance.find("slug", slug).firstResultOptional();
    }

    @Override
    public List<ProxyInstance> listAll() {
        return ProxyInstance.listAll();
    }

    @Override
    public boolean deleteById(Long id) {
        return ProxyInstance.deleteById(id);
    }
}
