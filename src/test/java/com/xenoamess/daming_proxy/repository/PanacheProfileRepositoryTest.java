package com.xenoamess.daming_proxy.repository;

import com.xenoamess.daming_proxy.entity.ProxyProfile;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PanacheProfileRepositoryTest {

    @Inject
    ProfileRepository profileRepository;

    @Test
    @TestTransaction
    void shouldSaveAndFindProfile() {
        ProxyProfile profile = new ProxyProfile("OpenAI", "repo-openai", "https://api.openai.com/v1");
        profile.bearerToken = "sk-test";
        profile.timeoutMs = 10000;

        ProxyProfile saved = profileRepository.save(profile);
        assertNotNull(saved.id);

        Optional<ProxyProfile> found = profileRepository.findById(saved.id);
        assertTrue(found.isPresent());
        assertEquals("repo-openai", found.get().slug);
        assertEquals("sk-test", found.get().bearerToken);
    }

    @Test
    @TestTransaction
    void shouldFindBySlug() {
        ProxyProfile profile = new ProxyProfile("Azure", "repo-azure", "https://azure.openai.com");
        profileRepository.save(profile);

        Optional<ProxyProfile> found = profileRepository.findBySlug("repo-azure");
        assertTrue(found.isPresent());
        assertEquals("Azure", found.get().name);
    }

    @Test
    @TestTransaction
    void shouldListAllProfiles() {
        profileRepository.save(new ProxyProfile("A", "repo-a", "http://a"));
        profileRepository.save(new ProxyProfile("B", "repo-b", "http://b"));

        List<ProxyProfile> all = profileRepository.listAll();
        assertTrue(all.size() >= 2);
    }

    @Test
    @TestTransaction
    void shouldDeleteProfile() {
        ProxyProfile profile = new ProxyProfile("DeleteMe", "repo-delete-me", "http://x");
        profileRepository.save(profile);

        boolean deleted = profileRepository.deleteById(profile.id);
        assertTrue(deleted);
        assertTrue(profileRepository.findById(profile.id).isEmpty());
    }
}
