package com.xenoamess.daming_proxy.plugin;

import com.xenoamess.daming_proxy.entity.Plugin;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class PluginExecutionService {

    @Inject
    Instance<PluginEngine> engines;

    public void executeRequestPlugins(List<Plugin> plugins, PluginContext context) {
        executePlugins(plugins, context, Plugin.ExecutionPhase.REQUEST, Plugin.ExecutionPhase.BOTH);
    }

    public void executeResponsePlugins(List<Plugin> plugins, PluginContext context) {
        executePlugins(plugins, context, Plugin.ExecutionPhase.RESPONSE, Plugin.ExecutionPhase.BOTH);
    }

    private void executePlugins(List<Plugin> plugins, PluginContext context,
                                Plugin.ExecutionPhase phase1, Plugin.ExecutionPhase phase2) {
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
                continue;
            }

            try {
                engine.execute(plugin, context);
            } catch (Exception e) {
                context.log("Plugin error [" + plugin.name + "]: " + e.getMessage());
            }
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
