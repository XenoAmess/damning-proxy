package com.xenoamess.damning_proxy.plugin.engine;

import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.plugin.PluginContext;
import com.xenoamess.damning_proxy.plugin.PluginEngine;
import com.xenoamess.damning_proxy.plugin.storage.PluginPackageStorage;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@ApplicationScoped
public class GroovyPluginEngine implements PluginEngine {

    private final GroovyShell shell = new GroovyShell();
    private final Map<String, Script> scriptCache = new HashMap<>();

    @Inject
    PluginPackageStorage packageStorage;

    @Override
    public boolean supports(Plugin.Language language) {
        return language == Plugin.Language.GROOVY;
    }

    @Override
    public void execute(Plugin plugin, PluginContext context) {
        String script = resolveScript(plugin);
        Script compiled = scriptCache.computeIfAbsent(cacheKey(plugin, script), s -> shell.parse(s));
        Binding binding = new Binding();
        binding.setVariable("context", context);
        if (plugin.mode == Plugin.Mode.ZIP_PACKAGE) {
            binding.setVariable("pluginDir", packageStorage.getPackagePath(plugin));
            binding.setVariable("readResource", (Function<String, byte[]>) path -> {
                try {
                    return packageStorage.readResourceBytes(plugin, path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            binding.setVariable("readResourceText", (Function<String, String>) path -> {
                try {
                    return packageStorage.readResourceText(plugin, path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        compiled.setBinding(binding);
        compiled.run();
    }

    private String resolveScript(Plugin plugin) {
        if (plugin.mode == Plugin.Mode.ZIP_PACKAGE) {
            try {
                return packageStorage.readMainScript(plugin);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read Groovy main script from plugin package", e);
            }
        }
        return plugin.script;
    }

    private String cacheKey(Plugin plugin, String script) {
        return plugin.id + ":" + plugin.mode + ":" + script.hashCode();
    }
}
