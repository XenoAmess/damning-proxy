package com.xenoamess.damning_proxy.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xenoamess.damning_proxy.entity.Plugin;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PluginExecutionServiceTest {

    @Inject
    PluginExecutionService service;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void shouldExecuteRequestPhasePlugin() {
        Plugin plugin = createPlugin("test-plugin", Plugin.Language.GROOVY,
            "context.setRequestHeader('X-Modified', 'true')",
            Plugin.ExecutionPhase.REQUEST);

        PluginContext ctx = new PluginContext();
        ctx.setRequestBody(Map.of("model", "gpt-3"));

        service.executeRequestPlugins(List.of(plugin), ctx);

        assertEquals("true", ctx.getRequestHeaders().get("X-Modified"));
        assertFalse(ctx.isStopped());
    }

    @Test
    void shouldSkipDisabledPlugin() {
        Plugin plugin = createPlugin("disabled-plugin", Plugin.Language.GROOVY,
            "context.setRequestHeader('X-Modified', 'true')",
            Plugin.ExecutionPhase.REQUEST);
        plugin.enabled = false;

        PluginContext ctx = new PluginContext();
        service.executeRequestPlugins(List.of(plugin), ctx);

        assertNull(ctx.getRequestHeaders().get("X-Modified"));
    }

    @Test
    void shouldSkipPluginWithWrongPhase() {
        Plugin plugin = createPlugin("response-plugin", Plugin.Language.GROOVY,
            "context.setRequestHeader('X-Modified', 'true')",
            Plugin.ExecutionPhase.RESPONSE);

        PluginContext ctx = new PluginContext();
        service.executeRequestPlugins(List.of(plugin), ctx);

        assertNull(ctx.getRequestHeaders().get("X-Modified"));
    }

    @Test
    void shouldStopChainOnPluginStop() {
        Plugin first = createPlugin("first", Plugin.Language.GROOVY,
            "context.stop()",
            Plugin.ExecutionPhase.REQUEST);
        Plugin second = createPlugin("second", Plugin.Language.GROOVY,
            "context.setRequestHeader('X-Second', 'true')",
            Plugin.ExecutionPhase.REQUEST);

        PluginContext ctx = new PluginContext();
        service.executeRequestPlugins(List.of(first, second), ctx);

        assertTrue(ctx.isStopped());
        assertNull(ctx.getRequestHeaders().get("X-Second"));
    }

    @Test
    void shouldRecordErrorOnPluginFailure() {
        Plugin plugin = createPlugin("bad-plugin", Plugin.Language.GROOVY,
            "throw new RuntimeException('test failure')",
            Plugin.ExecutionPhase.REQUEST);

        PluginContext ctx = new PluginContext();
        service.executeRequestPlugins(List.of(plugin), ctx);

        assertTrue(ctx.getPluginLogs().stream().anyMatch(msg -> msg.contains("Plugin error")));
    }

    private Plugin createPlugin(String name, Plugin.Language language, String script, Plugin.ExecutionPhase phase) {
        Plugin plugin = new Plugin();
        plugin.name = name;
        plugin.language = language;
        plugin.script = script;
        plugin.executionPhase = phase;
        plugin.enabled = true;
        plugin.id = (long) name.hashCode();
        return plugin;
    }
}
