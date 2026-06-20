package com.xenoamess.damning_proxy.repository;

import com.xenoamess.damning_proxy.entity.Plugin;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PanachePluginRepositoryTest {

    @Inject
    PluginRepository pluginRepository;

    @Inject
    PluginGroupRepository pluginGroupRepository;

    @BeforeEach
    @Transactional
    void cleanSlate() {
        // StartupMigration seeds two sample plugins ("大明战锤提示词（Groovy）" and
        // "大明战锤提示词（JS）") inside sample groups. Wipe groups first to drop
        // the FK rows, then plugins, so each test starts from a known state.
        pluginGroupRepository.listAll().forEach(g -> pluginGroupRepository.deleteById(g.id));
        pluginRepository.listAll().forEach(p -> pluginRepository.deleteById(p.id));
    }

    @Test
    @TestTransaction
    void shouldSaveAndFindPlugin() {
        Plugin plugin = new Plugin();
        plugin.name = "AddHeader";
        plugin.slug = "add-header";
        plugin.language = Plugin.Language.GROOVY;
        plugin.script = "context.requestHeaders['X-Test'] = '1'";
        plugin.executionPhase = Plugin.ExecutionPhase.REQUEST;

        Plugin saved = pluginRepository.save(plugin);
        assertNotNull(saved.id);

        List<Plugin> all = pluginRepository.listAll();
        assertTrue(all.stream().anyMatch(p -> p.id.equals(saved.id)));
    }

    @Test
    @TestTransaction
    void shouldListPluginsSortedByName() {
        Plugin p1 = new Plugin();
        p1.name = "B-Plugin";
        p1.slug = "b-plugin";
        p1.language = Plugin.Language.JS;
        p1.script = "";
        pluginRepository.save(p1);

        Plugin p2 = new Plugin();
        p2.name = "A-Plugin";
        p2.slug = "a-plugin";
        p2.language = Plugin.Language.GROOVY;
        p2.script = "";
        pluginRepository.save(p2);

        List<Plugin> found = pluginRepository.listAll();
        assertEquals(2, found.size());
        assertEquals("A-Plugin", found.get(0).name);
        assertEquals("B-Plugin", found.get(1).name);
    }

    @Test
    @TestTransaction
    void shouldDeletePlugin() {
        Plugin plugin = new Plugin();
        plugin.name = "DeleteMe";
        plugin.slug = "delete-me";
        plugin.language = Plugin.Language.JS;
        plugin.script = "";
        pluginRepository.save(plugin);

        boolean deleted = pluginRepository.deleteById(plugin.id);
        assertTrue(deleted);
        assertTrue(pluginRepository.findById(plugin.id).isEmpty());
    }
}
