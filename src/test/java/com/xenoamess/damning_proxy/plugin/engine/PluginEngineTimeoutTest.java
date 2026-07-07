package com.xenoamess.damning_proxy.plugin.engine;

import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.plugin.PluginContext;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(PluginEngineTimeoutTest.ShortTimeoutProfile.class)
class PluginEngineTimeoutTest {

    @Inject
    GroovyPluginEngine groovyPluginEngine;

    @Inject
    JavaScriptPluginEngine javaScriptPluginEngine;

    @Test
    void groovyShouldTimeoutOnLongRunningScript() {
        Plugin plugin = new Plugin();
        plugin.name = "SlowGroovy";
        plugin.language = Plugin.Language.GROOVY;
        plugin.mode = Plugin.Mode.SINGLE_SCRIPT;
        plugin.script = "java.lang.Thread.sleep(2000)";

        PluginContext context = new PluginContext();
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            groovyPluginEngine.execute(plugin, context)
        );

        assertTrue(ex.getMessage().contains("timed out"), ex.getMessage());
    }

    @Test
    void javaScriptShouldTimeoutOnLongRunningScript() {
        Plugin plugin = new Plugin();
        plugin.name = "SlowJs";
        plugin.id = 42L;
        plugin.language = Plugin.Language.JS;
        plugin.mode = Plugin.Mode.SINGLE_SCRIPT;
        plugin.script = "java.lang.Thread.sleep(2000);";

        PluginContext context = new PluginContext();
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            javaScriptPluginEngine.execute(plugin, context)
        );

        assertTrue(ex.getMessage().contains("timed out"), ex.getMessage());
    }

    public static class ShortTimeoutProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("damning-proxy.plugin.timeout-ms", "500");
        }
    }
}
