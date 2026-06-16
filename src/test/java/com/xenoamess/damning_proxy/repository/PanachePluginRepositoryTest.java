package com.xenoamess.damning_proxy.repository;

import com.xenoamess.damning_proxy.entity.Plugin;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PanachePluginRepositoryTest {

    @Inject
    PluginRepository pluginRepository;

    @Test
    @TestTransaction
    void shouldSaveAndFindPlugin() {
        Plugin plugin = new Plugin();
        plugin.name = "AddHeader";
        plugin.language = Plugin.Language.GROOVY;
        plugin.script = "context.requestHeaders['X-Test'] = '1'";
        plugin.priority = 10;
        plugin.executionPhase = Plugin.ExecutionPhase.REQUEST;
        plugin.globalScope = true;

        Plugin saved = pluginRepository.save(plugin);
        assertNotNull(saved.id);

        List<Plugin> globals = pluginRepository.findEnabledGlobal();
        assertTrue(globals.stream().anyMatch(p -> p.id.equals(saved.id)));
    }

    @Test
    @TestTransaction
    void shouldFindPluginsByProfileSorted() {
        Long profileId = 1L;

        Plugin p1 = new Plugin();
        p1.name = "P1";
        p1.language = Plugin.Language.JS;
        p1.script = "";
        p1.priority = 20;
        p1.profileId = profileId;
        p1.globalScope = false;
        pluginRepository.save(p1);

        Plugin p2 = new Plugin();
        p2.name = "P2";
        p2.language = Plugin.Language.GROOVY;
        p2.script = "";
        p2.priority = 10;
        p2.profileId = profileId;
        p2.globalScope = false;
        pluginRepository.save(p2);

        List<Plugin> found = pluginRepository.findEnabledByProfileId(profileId);
        assertEquals(2, found.size());
        assertEquals("P2", found.get(0).name);
        assertEquals("P1", found.get(1).name);
    }

    @Test
    @TestTransaction
    void shouldDeletePlugin() {
        Plugin plugin = new Plugin();
        plugin.name = "DeleteMe";
        plugin.language = Plugin.Language.JS;
        plugin.script = "";
        pluginRepository.save(plugin);

        boolean deleted = pluginRepository.deleteById(plugin.id);
        assertTrue(deleted);
        assertTrue(pluginRepository.findById(plugin.id).isEmpty());
    }
}
