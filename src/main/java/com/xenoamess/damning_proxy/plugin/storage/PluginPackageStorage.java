package com.xenoamess.damning_proxy.plugin.storage;

import com.xenoamess.damning_proxy.entity.Plugin;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PluginPackageStorage {

    private static final String BASE_DIR = System.getProperty("user.home") + "/.damning-proxy/plugins";

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
        Files.copy(zipInput, target, StandardCopyOption.REPLACE_EXISTING);
        return fileName;
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
            ZipArchiveEntry entry = zipFile.getEntry(normalized);
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
            zipFile.getEntries().asIterator().forEachRemaining(e -> entries.add(e.getName()));
        }
        return entries;
    }

    public Path getPackagePath(Plugin plugin) {
        return resolvePackagePath(plugin);
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
