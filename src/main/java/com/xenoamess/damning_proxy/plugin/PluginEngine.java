package com.xenoamess.damning_proxy.plugin;

import com.xenoamess.damning_proxy.entity.Plugin;

public interface PluginEngine {

    boolean supports(Plugin.Language language);

    void execute(Plugin plugin, PluginContext context);
}
