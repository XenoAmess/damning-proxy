package com.xenoamess.daming_proxy.plugin.engine;

import com.xenoamess.daming_proxy.entity.Plugin;
import com.xenoamess.daming_proxy.plugin.PluginContext;
import com.xenoamess.daming_proxy.plugin.PluginEngine;
import jakarta.enterprise.context.ApplicationScoped;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class JavaScriptPluginEngine implements PluginEngine {

    private final Map<String, Source> sourceCache = new HashMap<>();

    @Override
    public boolean supports(Plugin.Language language) {
        return language == Plugin.Language.JS;
    }

    @Override
    public void execute(Plugin plugin, PluginContext context) {
        Source source = sourceCache.computeIfAbsent(plugin.script, s -> {
            try {
                return Source.newBuilder("js", s, "plugin-" + plugin.id + ".js").build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try (Context jsContext = Context.newBuilder("js")
                .allowAllAccess(true)
                .build()) {
            Value bindings = jsContext.getBindings("js");
            bindings.putMember("context", context);
            jsContext.eval(source);
        }
    }
}
