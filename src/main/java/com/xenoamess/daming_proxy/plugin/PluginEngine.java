package com.xenoamess.daming_proxy.plugin;

import com.xenoamess.daming_proxy.entity.Plugin;

public interface PluginEngine {

    boolean supports(Plugin.Language language);

    void execute(Plugin plugin, PluginContext context);
}
