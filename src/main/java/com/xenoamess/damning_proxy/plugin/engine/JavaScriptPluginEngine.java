package com.xenoamess.damning_proxy.plugin.engine;

import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.plugin.PluginContext;
import com.xenoamess.damning_proxy.plugin.PluginEngine;
import com.xenoamess.damning_proxy.plugin.storage.PluginPackageStorage;
import com.xenoamess.damning_proxy.util.Hashes;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.openjdk.nashorn.api.scripting.ClassFilter;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class JavaScriptPluginEngine implements PluginEngine {

    // Cache the compiled script per (plugin.id, mode, scriptHash) so we don't pay the
    // Nashorn parse cost on every request. NashornScriptEngine instances are not
    // thread-safe, so we still create a fresh engine per execution but reuse the
    // already-parsed/validated script source.
    private final ConcurrentMap<String, String> scriptCache = new ConcurrentHashMap<>();

    private final NashornScriptEngineFactory engineFactory = new NashornScriptEngineFactory();

    @Inject
    PluginPackageStorage packageStorage;

    @Inject
    PluginSandbox sandbox;

    @ConfigProperty(name = "damning-proxy.plugin.timeout-ms", defaultValue = "30000")
    long scriptTimeoutMs = 30_000;

    @ConfigProperty(name = "damning-proxy.plugin.execution.pool-size", defaultValue = "16")
    int poolSize = 16;

    private ClassFilter classFilter;
    private ClassLoader engineClassLoader;

    private ExecutorService scriptExecutor;

    public JavaScriptPluginEngine() {
        // Used when the engine is instantiated outside of CDI (e.g. unit tests).
        // Defaults to a sandboxed configuration.
        initSandbox(new PluginSandbox());
        this.scriptExecutor = createExecutor(16);
    }

    @PostConstruct
    void init() {
        // Reinitialize with the CDI-injected sandbox so configuration is honored.
        initSandbox(sandbox != null ? sandbox : new PluginSandbox());
        if (scriptExecutor != null) {
            scriptExecutor.shutdownNow();
        }
        this.scriptExecutor = createExecutor(poolSize);
    }

    private ExecutorService createExecutor(int size) {
        return new ThreadPoolExecutor(
            size, size,
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(size * 10),
            r -> {
                Thread t = new Thread(r, "js-plugin-exec");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private void initSandbox(PluginSandbox sandbox) {
        engineClassLoader = this.getClass().getClassLoader();
        classFilter = sandbox.isEnabled() ? sandbox::isClassAllowed : null;
        scriptCache.clear();
    }

    @PreDestroy
    void shutdown() {
        scriptExecutor.shutdownNow();
    }

    @Override
    public boolean supports(Plugin.Language language) {
        return language == Plugin.Language.JS;
    }

    @Override
    public void execute(Plugin plugin, PluginContext context) {
        String script = resolveScript(plugin);

        Future<?> future = null;
        try {
            future = scriptExecutor.submit(() -> {
                try {
                    // Compile and execute on the same thread to avoid double-compile
                    ensureCompiled(plugin, script);
                    ScriptEngine engine = createEngine();
                    engine.put("context", context);
                    if (plugin.mode == Plugin.Mode.ZIP_PACKAGE) {
                        engine.put("readResource", new ResourceReader(plugin, false, packageStorage));
                        engine.put("readResourceText", new ResourceReader(plugin, true, packageStorage));
                    }
                    try {
                        engine.eval("(function(){\n" + script + "\n})();");
                    } catch (ScriptException e) {
                        throw new RuntimeException("JavaScript script execution error: " + e.getMessage(), e);
                    }
                } finally {
                    engineCache.remove();
                }
            });
            future.get(scriptTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            if (future != null) {
                future.cancel(true);
            }
            throw new RuntimeException("JavaScript plugin timed out after " + (scriptTimeoutMs / 1000) + "s: " + plugin.name);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute JavaScript plugin: " + plugin.name, e);
        }
    }

    private void ensureCompiled(Plugin plugin, String script) {
        String cacheKey = cacheKey(plugin, script);
        scriptCache.computeIfAbsent(cacheKey, k -> {
            // Force the engine to parse the script once to catch syntax errors at
            // plugin-load time instead of on the first request. We bind a real
            // PluginContext so the IIFE can run any access pattern; runtime
            // errors during this dry run are swallowed because they usually mean
            // the script just touched a body field that the dummy context doesn't
            // have – the real execute() call supplies the actual request body.
            ScriptEngine engine = createEngine();
            try {
                PluginContext dummy = new PluginContext();
                dummy.setRequestBody(new java.util.LinkedHashMap<String, Object>() {{
                    put("messages", new java.util.ArrayList<>());
                }});
                dummy.setResponseBody(new java.util.LinkedHashMap<String, Object>());
                engine.put("context", dummy);
                engine.eval("(function(){\n" + script + "\n})();");
            } catch (ScriptException e) {
                throw new RuntimeException(
                    "Failed to compile JavaScript plugin '" + plugin.name + "': " + e.getMessage(), e);
            } catch (RuntimeException e) {
                io.quarkus.logging.Log.warnf(
                    "JavaScript plugin '%s' pre-validation warning: %s",
                    plugin.name, e.getMessage());
            } finally {
                engineCache.remove();
            }
            return script;
        });
    }

    public String validate(Plugin plugin) {
        String script = resolveScript(plugin);
        ScriptEngine engine = createEngine();
        try {
            PluginContext dummy = new PluginContext();
            dummy.setRequestBody(new java.util.LinkedHashMap<String, Object>() {{
                put("messages", new java.util.ArrayList<>());
            }});
            dummy.setResponseBody(new java.util.LinkedHashMap<String, Object>());
            engine.put("context", dummy);
            engine.eval("(function(){\n" + script + "\n})();");
            return null;
        } catch (ScriptException e) {
            return "JavaScript compile error: " + e.getMessage();
        } catch (RuntimeException e) {
            return "JavaScript validation error: " + e.getMessage();
        } finally {
            engineCache.remove();
        }
    }

    public void evictCache(Plugin plugin) {
        String script = resolveScript(plugin);
        scriptCache.remove(cacheKey(plugin, script));
    }

    private String resolveScript(Plugin plugin) {
        if (plugin.mode == Plugin.Mode.ZIP_PACKAGE) {
            try {
                return packageStorage.readMainScript(plugin);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read JavaScript main script from plugin package", e);
            }
        }
        return plugin.script;
    }

    private String cacheKey(Plugin plugin, String script) {
        return plugin.id + ":" + plugin.mode + ":" + Hashes.sha256Hex(script);
    }

    // Nashorn ScriptEngine instances are not thread-safe. Cache them per-thread
    // via ThreadLocal to avoid the heavy creation cost on every execution.
    private final ThreadLocal<ScriptEngine> engineCache = ThreadLocal.withInitial(this::createEngine);

    private ScriptEngine createEngine() {
        if (classFilter != null) {
            return engineFactory.getScriptEngine(new String[]{"--language=es6"}, engineClassLoader, classFilter);
        }
        return engineFactory.getScriptEngine(new String[]{"--language=es6"});
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

        @org.openjdk.nashorn.internal.objects.annotations.Function
        public Object read(String path) throws IOException {
            byte[] bytes = packageStorage.readResourceBytes(plugin, path);
            if (asText) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            return bytes;
        }
    }
}