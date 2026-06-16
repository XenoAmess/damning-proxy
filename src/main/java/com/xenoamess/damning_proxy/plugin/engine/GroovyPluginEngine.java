package com.xenoamess.damning_proxy.plugin.engine;

import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.plugin.PluginContext;
import com.xenoamess.damning_proxy.plugin.PluginEngine;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class GroovyPluginEngine implements PluginEngine {

    private final GroovyShell shell = new GroovyShell();
    private final Map<String, Script> scriptCache = new HashMap<>();

    @Override
    public boolean supports(Plugin.Language language) {
        return language == Plugin.Language.GROOVY;
    }

    @Override
    public void execute(Plugin plugin, PluginContext context) {
        Script script = scriptCache.computeIfAbsent(plugin.script, s -> shell.parse(s));
        Binding binding = new Binding();
        binding.setVariable("context", context);
        script.setBinding(binding);
        script.run();
    }
}
