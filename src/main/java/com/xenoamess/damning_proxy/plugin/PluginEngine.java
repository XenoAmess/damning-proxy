package com.xenoamess.damning_proxy.plugin;

import com.xenoamess.damning_proxy.entity.Plugin;

public interface PluginEngine {

    boolean supports(Plugin.Language language);

    void execute(Plugin plugin, PluginContext context);

    default void evictCache(Plugin plugin) {
        // no-op by default
    }

    /**
     * Validates the plugin script without executing it. Returns null if valid,
     * or a human-readable error message if compilation fails.
     */
    default String validate(Plugin plugin) {
        return null;
    }
}
