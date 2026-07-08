package com.xenoamess.damning_proxy.plugin.storage;

import com.xenoamess.damning_proxy.entity.Plugin;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@ApplicationScoped
public class PluginPackageStorage {

    private static final String BASE_DIR = System.getProperty("user.home") + "/.damning-proxy/plugins";

    @ConfigProperty(name = "damning-proxy.plugin.zip.max-size-bytes", defaultValue = "10485760")
    long maxZipSizeBytes;

    @ConfigProperty(name = "damning-proxy.plugin.zip.max-entries", defaultValue = "100")
    int maxZipEntries;

    @ConfigProperty(name = "damning-proxy.plugin.zip.max-entry-size-bytes", defaultValue = "1048576")
    long maxEntrySizeBytes;

    @Inject
    public PluginPackageStorage() {
        try {
            Files.createDirectories(Path.of(BASE_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create plugin storage directory", e);
        }
    }

    public Path resolvePackagePath(Plugin plugin) {
        if (plugin.packagePath == null || plugin.packagePath.isBlank()) {
            return null;
        }
        return Path.of(BASE_DIR).resolve(plugin.packagePath).toAbsolutePath().normalize();
    }

    public boolean packageExists(Plugin plugin) {
        Path path = resolvePackagePath(plugin);
        return path != null && Files.exists(path);
    }

    public String storePackage(Plugin plugin, InputStream zipInput) throws IOException {
        String fileName = "plugin-" + plugin.slug + "-" + UUID.randomUUID() + ".zip";
        Path target = Path.of(BASE_DIR).resolve(fileName);
        Path temp = Path.of(BASE_DIR).resolve(fileName + ".tmp");
        try {
            Files.copy(zipInput, temp, StandardCopyOption.REPLACE_EXISTING);
            validateZipFile(temp, plugin);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (IllegalArgumentException e) {
            Files.deleteIfExists(temp);
            throw e;
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    public void deletePackage(Plugin plugin) {
        Path path = resolvePackagePath(plugin);
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                Log.warnf("Failed to delete plugin package %s: %s", path, e.getMessage());
            }
        }
    }

    public byte[] readResourceBytes(Plugin plugin, String resourcePath) throws IOException {
        Path packagePath = resolvePackagePath(plugin);
        if (packagePath == null || !Files.exists(packagePath)) {
            throw new IOException("Plugin package not found");
        }
        String normalized = normalizeResourcePath(resourcePath);
        try (ZipFile zipFile = new ZipFile(packagePath.toFile())) {
            ZipEntry entry = zipFile.getEntry(normalized);
            if (entry == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            try (InputStream is = zipFile.getInputStream(entry)) {
                return is.readAllBytes();
            }
        }
    }

    public String readResourceText(Plugin plugin, String resourcePath) throws IOException {
        return new String(readResourceBytes(plugin, resourcePath), StandardCharsets.UTF_8);
    }

    public String readMainScript(Plugin plugin) throws IOException {
        String entryName = plugin.language == Plugin.Language.GROOVY ? "main.groovy" : "main.js";
        return readResourceText(plugin, entryName);
    }

    public List<String> listEntries(Plugin plugin) throws IOException {
        Path packagePath = resolvePackagePath(plugin);
        List<String> entries = new ArrayList<>();
        if (packagePath == null || !Files.exists(packagePath)) {
            return entries;
        }
        try (ZipFile zipFile = new ZipFile(packagePath.toFile())) {
            Collections.list(zipFile.entries()).forEach(e -> entries.add(e.getName()));
        }
        return entries;
    }

    public Path getPackagePath(Plugin plugin) {
        return resolvePackagePath(plugin);
    }

    private void validateZipFile(Path zipPath, Plugin plugin) throws IOException {
        long size = Files.size(zipPath);
        if (size > maxZipSizeBytes) {
            throw new IllegalArgumentException(
                "Plugin ZIP package too large: " + size + " bytes (max " + maxZipSizeBytes + ")");
        }
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            List<ZipEntry> entries = new ArrayList<>();
            Collections.list(zipFile.entries()).forEach(entries::add);
            if (entries.size() > maxZipEntries) {
                throw new IllegalArgumentException(
                    "Plugin ZIP package has too many entries: " + entries.size() + " (max " + maxZipEntries + ")");
            }
            String expectedMain = plugin.language == Plugin.Language.GROOVY ? "main.groovy" : "main.js";
            boolean hasMain = false;
            for (ZipEntry entry : entries) {
                String name = entry.getName();
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("Plugin ZIP package contains an entry with an empty name");
                }
                normalizeResourcePath(name);
                long entrySize = entry.getSize();
                if (entrySize < 0) {
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        entrySize = is.readAllBytes().length;
                    }
                }
                if (entrySize > maxEntrySizeBytes) {
                    throw new IllegalArgumentException(
                        "Plugin ZIP entry too large: " + name + " (" + entrySize + " bytes, max " + maxEntrySizeBytes + ")");
                }
                if (name.equals(expectedMain)) {
                    hasMain = true;
                }
            }
            if (!hasMain) {
                throw new IllegalArgumentException(
                    "Plugin ZIP package must contain " + expectedMain);
            }
        }
    }

    private String normalizeResourcePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace("\\", "/").replaceAll("/+", "/").replaceAll("^/+", "");
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("Invalid resource path: " + path);
        }
        return normalized;
    }
}
