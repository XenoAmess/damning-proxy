package com.xenoamess.damning_proxy.plugin.engine;

import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.plugin.PluginContext;
import com.xenoamess.damning_proxy.plugin.PluginEngine;
import com.xenoamess.damning_proxy.plugin.storage.PluginPackageStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class JavaScriptPluginEngine implements PluginEngine {

    private final Map<String, Source> sourceCache = new HashMap<>();

    @Inject
    PluginPackageStorage packageStorage;

    @Override
    public boolean supports(Plugin.Language language) {
        return language == Plugin.Language.JS;
    }

    @Override
    public void execute(Plugin plugin, PluginContext context) {
        String script = resolveScript(plugin);
        Source source = sourceCache.computeIfAbsent(cacheKey(plugin, script), s -> {
            try {
                return Source.newBuilder("js", s, "plugin-" + plugin.id + ".js").build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try (Context jsContext = Context.newBuilder("js")
                .allowAllAccess(true)
                .allowIO(false)
                .build()) {
            Value bindings = jsContext.getBindings("js");
            bindings.putMember("context", context);
            if (plugin.mode == Plugin.Mode.ZIP_PACKAGE) {
                bindings.putMember("readResource", new ResourceReader(plugin, false, packageStorage));
                bindings.putMember("readResourceText", new ResourceReader(plugin, true, packageStorage));
            }
            jsContext.eval(source);
        }
    }

    private String resolveScript(Plugin plugin) {
        if (plugin.mode == Plugin.Mode.ZIP_PACKAGE) {
            try {
                return packageStorage.readMainScript(plugin);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read JS main script from plugin package", e);
            }
        }
        return plugin.script;
    }

    private String cacheKey(Plugin plugin, String script) {
        return plugin.id + ":" + plugin.mode + ":" + script.hashCode();
    }

    public static class ResourceReader {
        private final Plugin plugin;
        private final boolean asText;
        private final PluginPackageStorage packageStorage;

        public ResourceReader(Plugin plugin, boolean asText, PluginPackageStorage packageStorage) {
            this.plugin = plugin;
            this.asText = asText;
            this.packageStorage = packageStorage;
        }

        @HostAccess.Export
        public Object read(String path) throws IOException {
            byte[] bytes = packageStorage.readResourceBytes(plugin, path);
            if (asText) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            return bytes;
        }
    }
}
