package com.xenoamess.damning_proxy.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipFile;

import com.xenoamess.damning_proxy.util.Validation;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class DatabaseAdminService {

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    String jdbcUrl;

    @ConfigProperty(name = "quarkus.datasource.username", defaultValue = "sa")
    String username;

    @ConfigProperty(name = "quarkus.datasource.password", defaultValue = "sa")
    String password;

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Transactional
    public BackupResult backup(String requestedName) {
        if (isInMemoryDatabase()) {
            throw new IllegalStateException("H2 BACKUP is not supported for in-memory databases");
        }
        Path targetPath = resolveBackupPath(requestedName);
        Path parent = targetPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create backup directory: " + parent, e);
            }
        }
        String absolute = targetPath.toAbsolutePath().toString();
        Query query = entityManager.createNativeQuery("BACKUP TO ?");
        query.setParameter(1, absolute);
        query.executeUpdate();
        Log.infof("Database backup created: %s", absolute);
        return new BackupResult(absolute, Files.exists(targetPath));
    }

    public RestoreResult prepareRestore(String requestedPath) {
        Path sourcePath = Paths.get(requestedPath).toAbsolutePath().normalize();
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("Backup file does not exist: " + sourcePath);
        }
        validateH2Backup(sourcePath);

        Path dataDir = Paths.get(System.getProperty("user.home"), ".damning-proxy").toAbsolutePath().normalize();
        Path targetPath = dataDir.resolve("data.restore.zip");
        try {
            Files.createDirectories(dataDir);
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to stage restore file: " + targetPath, e);
        }
        Log.infof("Restore file staged: %s", targetPath);
        return new RestoreResult(targetPath.toString(), buildRestartCommand());
    }

    private void validateH2Backup(Path path) {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            long entries = zip.size();
            if (entries == 0) {
                throw new IllegalArgumentException("Backup file is empty: " + path);
            }
            boolean hasDbFile = zip.stream().anyMatch(e -> e.getName().endsWith(".db") || e.getName().endsWith(".mv.db"));
            if (!hasDbFile) {
                throw new IllegalArgumentException("Backup file does not contain a valid H2 database file: " + path);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid H2 backup file: " + path, e);
        }
    }

    private Path resolveBackupPath(String requestedName) {
        Path dataDir = Paths.get(System.getProperty("user.home"), ".damning-proxy", "backups").toAbsolutePath().normalize();
        String name = requestedName;
        if (name == null || name.isBlank()) {
            name = "backup_" + LocalDateTime.now().format(TIMESTAMP) + ".zip";
        }
        Validation.validatePathName(name);
        Path resolved = dataDir.resolve(name).normalize();
        if (!resolved.startsWith(dataDir)) {
            throw new IllegalArgumentException("Backup path must be inside backup directory: " + dataDir);
        }
        return resolved;
    }

    private boolean isInMemoryDatabase() {
        return jdbcUrl != null && jdbcUrl.contains("jdbc:h2:mem:");
    }

    private String buildRestartCommand() {
        String home = System.getProperty("user.home");
        Path dataDir = Paths.get(home, ".damning-proxy").toAbsolutePath().normalize();
        return String.format(
            "Stop the application, then run:\n"
                + "cd %s && unzip -o data.restore.zip -d %s && rm data.restore.zip && restart the application",
            dataDir, dataDir);
    }

    public record BackupResult(String path, boolean success) {
    }

    public record RestoreResult(String stagedPath, String restartCommand) {
    }
}
