package com.xenoamess.damning_proxy.plugin.engine;

import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.plugin.PluginContext;
import com.xenoamess.damning_proxy.plugin.PluginEngine;
import com.xenoamess.damning_proxy.plugin.storage.PluginPackageStorage;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import com.xenoamess.damning_proxy.util.Hashes;

@ApplicationScoped
public class GroovyPluginEngine implements PluginEngine {

    private GroovyShell shell;
    // Cache the compiled Script *class*, not instance: Script objects are not thread-safe
    // and should not be reused across concurrent requests.
    private final Map<String, Class<? extends Script>> scriptClassCache = new ConcurrentHashMap<>();

    @Inject
    PluginPackageStorage packageStorage;

    @Inject
    PluginSandbox sandbox;

    @ConfigProperty(name = "damning-proxy.plugin.timeout-ms", defaultValue = "30000")
    long scriptTimeoutMs = 30_000;

    @ConfigProperty(name = "damning-proxy.plugin.execution.pool-size", defaultValue = "16")
    int poolSize = 16;

    private ExecutorService scriptExecutor;

    public GroovyPluginEngine() {
        // Used when the engine is instantiated outside of CDI (e.g. unit tests).
        // Defaults to a sandboxed configuration.
        initShell(new PluginSandbox());
        this.scriptExecutor = createExecutor(16);
    }

    @PostConstruct
    void init() {
        // Reinitialize with the CDI-injected sandbox so configuration is honored.
        initShell(sandbox != null ? sandbox : new PluginSandbox());
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
                Thread t = new Thread(r, "groovy-plugin-exec");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private void initShell(PluginSandbox sandbox) {
        CompilerConfiguration config = new CompilerConfiguration();
        if (sandbox.isEnabled()) {
            SecureASTCustomizer customizer = new SecureASTCustomizer();
            customizer.setDisallowedImports(new ArrayList<>(sandbox.getDeniedClasses()));
            customizer.setDisallowedStarImports(new ArrayList<>(sandbox.getDeniedPackages()));
            customizer.setDisallowedReceivers(new ArrayList<>(sandbox.getDeniedClasses()));
            config.addCompilationCustomizers(customizer);
        }
        shell = new GroovyShell(config);
        scriptClassCache.clear();
    }

    @PreDestroy
    void shutdown() {
        scriptExecutor.shutdownNow();
    }

    @Override
    public boolean supports(Plugin.Language language) {
        return language == Plugin.Language.GROOVY;
    }

    @Override
    public void execute(Plugin plugin, PluginContext context) {
        String script = resolveScript(plugin);
        Class<? extends Script> scriptClass = scriptClassCache.computeIfAbsent(cacheKey(plugin, script), k -> shell.parse(script).getClass());
        Binding binding = new Binding();
        binding.setVariable("context", context);
        if (plugin.mode == Plugin.Mode.ZIP_PACKAGE) {
            binding.setVariable("pluginDir", packageStorage.getPackagePath(plugin));
            binding.setVariable("readResource", (Function<String, byte[]>) path -> {
                try {
                    return packageStorage.readResourceBytes(plugin, path);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read resource: " + path, e);
                }
            });
            binding.setVariable("readResourceText", (Function<String, String>) path -> {
                try {
                    return packageStorage.readResourceText(plugin, path);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read resource: " + path, e);
                }
            });
        }
        Future<?> future = null;
        try {
            Script instance = scriptClass.getDeclaredConstructor(Binding.class).newInstance(binding);
            future = scriptExecutor.submit(() -> instance.run());
            future.get(scriptTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            if (future != null) {
                future.cancel(true);
            }
            throw new RuntimeException("Groovy plugin timed out after " + (scriptTimeoutMs / 1000) + "s: " + plugin.name);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute Groovy plugin: " + plugin.name, e);
        }
    }

    public String validate(Plugin plugin) {
        String script = resolveScript(plugin);
        try {
            shell.parse(script);
            return null;
        } catch (Exception e) {
            return "Groovy compile error: " + e.getMessage();
        }
    }

    public void evictCache(Plugin plugin) {
        String script = resolveScript(plugin);
        scriptClassCache.remove(cacheKey(plugin, script));
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
        return plugin.id + ":" + plugin.mode + ":" + Hashes.sha256Hex(script);
    }
}
