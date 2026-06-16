package com.xenoamess.damning_proxy.repository;

import com.xenoamess.damning_proxy.entity.ProxyProfile;

import java.util.List;
import java.util.Optional;

public interface ProfileRepository {

    ProxyProfile save(ProxyProfile profile);

    Optional<ProxyProfile> findById(Long id);

    Optional<ProxyProfile> findBySlug(String slug);

    List<ProxyProfile> listAll();

    boolean deleteById(Long id);
}
