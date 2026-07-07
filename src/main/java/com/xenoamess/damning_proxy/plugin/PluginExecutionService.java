package com.xenoamess.damning_proxy.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xenoamess.damning_proxy.entity.Plugin;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class PluginExecutionService {

    @Inject
    Instance<PluginEngine> engines;

    @Inject
    ObjectMapper objectMapper;

    public void executeRequestPlugins(List<Plugin> plugins, PluginContext context) {
        executePlugins(plugins, context, Plugin.ExecutionPhase.REQUEST, Plugin.ExecutionPhase.BOTH);
    }

    public void executeResponsePlugins(List<Plugin> plugins, PluginContext context) {
        executePlugins(plugins, context, Plugin.ExecutionPhase.RESPONSE, Plugin.ExecutionPhase.BOTH);
    }

    public void executeStreamChunkPlugins(List<Plugin> plugins, PluginContext context) {
        executePlugins(plugins, context, Plugin.ExecutionPhase.STREAM_CHUNK, Plugin.ExecutionPhase.STREAM_CHUNK);
    }

    private void executePlugins(List<Plugin> plugins, PluginContext context,
                                Plugin.ExecutionPhase phase1, Plugin.ExecutionPhase phase2) {
        Object previousAfterBody = null;
        for (Plugin plugin : plugins) {
            if (!plugin.enabled || context.isStopped() || context.isReturned()) {
                break;
            }
            if (plugin.executionPhase != phase1 && plugin.executionPhase != phase2) {
                continue;
            }

            PluginEngine engine = findEngine(plugin.language);
            if (engine == null) {
                context.log("No engine for language: " + plugin.language);
                context.getFriendlyLogCollector().add(
                    plugin.id, plugin.name, phase1.name(), context.getRequestBody(), context.getRequestBody(),
                    true, "No engine for language: " + plugin.language
                );
                continue;
            }

            Object beforeBody;
            if (previousAfterBody != null) {
                beforeBody = previousAfterBody;
            } else {
                beforeBody = phase1 == Plugin.ExecutionPhase.REQUEST
                    ? deepCopy(context.getRequestBody())
                    : deepCopy(context.getResponseBody());
            }
            try {
                engine.execute(plugin, context);
                Object afterBody = phase1 == Plugin.ExecutionPhase.REQUEST
                    ? context.getRequestBody()
                    : context.getResponseBody();
                previousAfterBody = deepCopy(afterBody);
                context.getFriendlyLogCollector().add(
                    plugin.id, plugin.name, phase1.name(), beforeBody, afterBody, false, null
                );
            } catch (Exception e) {
                previousAfterBody = null;
                context.log("Plugin error [" + plugin.name + "]: " + e.getMessage());
                context.getFriendlyLogCollector().add(
                    plugin.id, plugin.name, phase1.name(), beforeBody, beforeBody, true,
                    "Plugin error [" + plugin.name + "]: " + e.getMessage()
                );
            }
        }
    }

    private Object deepCopy(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(value), Object.class);
        } catch (Exception e) {
            return value;
        }
    }

    private PluginEngine findEngine(Plugin.Language language) {
        for (PluginEngine engine : engines) {
            if (engine.supports(language)) {
                return engine;
            }
        }
        return null;
    }
}
