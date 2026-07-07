package com.xenoamess.damning_proxy.plugin.engine;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class PluginSandbox {

    @ConfigProperty(name = "damning-proxy.plugin.sandbox.enabled", defaultValue = "true")
    boolean enabled = true;

    @ConfigProperty(name = "damning-proxy.plugin.sandbox.denied-classes")
    Optional<List<String>> deniedClasses = Optional.empty();

    @ConfigProperty(name = "damning-proxy.plugin.sandbox.denied-packages")
    Optional<List<String>> deniedPackages = Optional.empty();

    private volatile Set<String> deniedClassSet;
    private volatile Set<String> deniedPackageSet;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isClassAllowed(String className) {
        if (!enabled) {
            return true;
        }
        ensureInitialized();
        if (deniedClassSet.contains(className)) {
            return false;
        }
        for (String pkg : deniedPackageSet) {
            if (className.startsWith(pkg + ".")) {
                return false;
            }
        }
        return true;
    }

    public Set<String> getDeniedClasses() {
        ensureInitialized();
        return deniedClassSet;
    }

    public Set<String> getDeniedPackages() {
        ensureInitialized();
        return deniedPackageSet;
    }

    private void ensureInitialized() {
        if (deniedClassSet != null) {
            return;
        }
        synchronized (this) {
            if (deniedClassSet != null) {
                return;
            }
            List<String> classes = deniedClasses.orElseGet(PluginSandbox::defaultDeniedClasses);
            List<String> packages = deniedPackages.orElseGet(PluginSandbox::defaultDeniedPackages);
            deniedClassSet = normalize(classes);
            deniedPackageSet = normalize(packages);
        }
    }

    private static Set<String> normalize(List<String> values) {
        return values.stream()
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toSet());
    }

    private static List<String> defaultDeniedClasses() {
        return List.of(
            "java.io.File",
            "java.io.FileInputStream",
            "java.io.FileOutputStream",
            "java.io.FileReader",
            "java.io.FileWriter",
            "java.io.RandomAccessFile",
            "java.net.URL",
            "java.net.URLConnection",
            "java.net.HttpURLConnection",
            "java.net.Socket",
            "java.net.ServerSocket",
            "java.net.DatagramSocket",
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.nio.file.Files",
            "java.nio.file.Paths",
            "java.nio.file.Path",
            "sun.misc.Unsafe"
        );
    }

    private static List<String> defaultDeniedPackages() {
        return List.of(
            "java.io",
            "java.net",
            "java.nio.file",
            "java.lang.reflect"
        );
    }
}
