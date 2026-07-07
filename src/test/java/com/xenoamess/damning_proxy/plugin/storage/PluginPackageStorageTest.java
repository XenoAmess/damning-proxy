package com.xenoamess.damning_proxy.plugin.storage;

import com.xenoamess.damning_proxy.entity.Plugin;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(PluginPackageStorageTest.SmallLimitsProfile.class)
class PluginPackageStorageTest {

    @Inject
    PluginPackageStorage storage;

    private final List<Plugin> createdPlugins = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdPlugins.forEach(storage::deletePackage);
        createdPlugins.clear();
    }

    @Test
    void shouldStoreValidGroovyZip() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("main.groovy", "context.stop()".getBytes());
        Plugin plugin = track(plugin("valid-groovy", Plugin.Language.GROOVY));

        String path = storage.storePackage(plugin, new ByteArrayInputStream(buildZip(entries)));

        plugin.packagePath = path;
        assertNotNull(path);
        assertTrue(storage.packageExists(plugin));
    }

    @Test
    void shouldStoreValidJsZip() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("main.js", "context.stop()".getBytes());
        entries.put("assets/info.txt", "info".getBytes());
        Plugin plugin = track(plugin("valid-js", Plugin.Language.JS));

        String path = storage.storePackage(plugin, new ByteArrayInputStream(buildZip(entries)));

        plugin.packagePath = path;
        assertNotNull(path);
        assertTrue(storage.packageExists(plugin));
    }

    @Test
    void shouldRejectZipMissingMainEntry() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("other.txt", "content".getBytes());
        Plugin plugin = track(plugin("missing-main", Plugin.Language.GROOVY));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            storage.storePackage(plugin, new ByteArrayInputStream(buildZip(entries)))
        );

        assertTrue(ex.getMessage().contains("must contain main.groovy"), ex.getMessage());
        assertFalse(storage.packageExists(plugin));
    }

    @Test
    void shouldRejectZipWithTooManyEntries() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            entries.put("file" + i + ".txt", "x".getBytes());
        }
        entries.put("main.groovy", "context.stop()".getBytes());
        Plugin plugin = track(plugin("too-many-entries", Plugin.Language.GROOVY));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            storage.storePackage(plugin, new ByteArrayInputStream(buildZip(entries)))
        );

        assertTrue(ex.getMessage().contains("too many entries"), ex.getMessage());
    }

    @Test
    void shouldRejectZipWithEntryTooLarge() throws Exception {
        byte[] bigEntry = new byte[1024];
        Arrays.fill(bigEntry, (byte) 'x');
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("main.groovy", bigEntry);
        Plugin plugin = track(plugin("entry-too-large", Plugin.Language.GROOVY));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            storage.storePackage(plugin, new ByteArrayInputStream(buildZip(entries)))
        );

        assertTrue(ex.getMessage().contains("entry too large"), ex.getMessage());
    }

    @Test
    void shouldRejectZipExceedingMaxSize() throws Exception {
        Random random = new Random(0);
        byte[] bigEntry = new byte[2 * 1024 * 1024];
        random.nextBytes(bigEntry);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("main.groovy", bigEntry);
        Plugin plugin = track(plugin("zip-too-large", Plugin.Language.GROOVY));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            storage.storePackage(plugin, new ByteArrayInputStream(buildZip(entries)))
        );

        assertTrue(ex.getMessage().contains("too large"), ex.getMessage());
    }

    @Test
    void shouldRejectZipWithPathTraversalEntry() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("main.groovy", "context.stop()".getBytes());
        entries.put("../evil.txt", "evil".getBytes());
        Plugin plugin = track(plugin("path-traversal", Plugin.Language.GROOVY));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            storage.storePackage(plugin, new ByteArrayInputStream(buildZip(entries)))
        );

        assertTrue(ex.getMessage().contains("Invalid resource path"), ex.getMessage());
    }

    private Plugin plugin(String slug, Plugin.Language language) {
        Plugin plugin = new Plugin();
        plugin.slug = slug;
        plugin.language = language;
        plugin.mode = Plugin.Mode.ZIP_PACKAGE;
        return plugin;
    }

    private Plugin track(Plugin plugin) {
        createdPlugins.add(plugin);
        return plugin;
    }

    private byte[] buildZip(Map<String, byte[]> entries) throws Exception {
        return ZipBuilder.buildZip(entries);
    }

    public static class SmallLimitsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "damning-proxy.plugin.zip.max-size-bytes", "1024",
                "damning-proxy.plugin.zip.max-entries", "3",
                "damning-proxy.plugin.zip.max-entry-size-bytes", "512"
            );
        }
    }
}
