package com.xenoamess.damning_proxy.api.admin;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(DatabaseAdminApiTest.FileDatabaseProfile.class)
class DatabaseAdminApiTest {

    @Inject
    EntityManager entityManager;

    Path backupDir;
    final List<Path> createdBackups = new ArrayList<>();

    @BeforeEach
    void setUp() {
        backupDir = Paths.get(System.getProperty("user.home"), ".damning-proxy", "backups").toAbsolutePath().normalize();
        createdBackups.clear();
    }

    @AfterEach
    void tearDown() throws IOException {
        for (Path p : createdBackups) {
            Files.deleteIfExists(p);
        }
        Path restoreMarker = Paths.get(System.getProperty("user.home"), ".damning-proxy", "data.restore.zip").toAbsolutePath().normalize();
        Files.deleteIfExists(restoreMarker);
    }

    @Test
    @Transactional
    void shouldBackupDatabaseToZip() {
        // ensure at least one row exists so the backup is non-empty
        entityManager.createNativeQuery("SELECT 1").getSingleResult();

        DatabaseAdminApiTest.BackupResponse response = RestAssured.given()
            .queryParam("name", "test_backup.zip")
            .when()
            .post("/api/admin/database/backup")
            .then()
            .statusCode(200)
            .extract()
            .as(DatabaseAdminApiTest.BackupResponse.class);

        assertTrue(response.success);
        assertNotNull(response.path);
        assertTrue(Files.exists(Paths.get(response.path)));
        createdBackups.add(Paths.get(response.path));

        try (ZipFile zip = new ZipFile(response.path)) {
            assertTrue(zip.size() > 0, "backup zip should not be empty");
            boolean hasDb = zip.stream().anyMatch(e -> e.getName().endsWith(".db") || e.getName().endsWith(".mv.db"));
            assertTrue(hasDb, "backup should contain an H2 database file");
        } catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void shouldGenerateDefaultBackupName() {
        DatabaseAdminApiTest.BackupResponse response = RestAssured.given()
            .when()
            .post("/api/admin/database/backup")
            .then()
            .statusCode(200)
            .extract()
            .as(DatabaseAdminApiTest.BackupResponse.class);

        assertTrue(response.success);
        assertNotNull(response.path);
        assertTrue(response.path.endsWith(".zip"));
        assertTrue(Files.exists(Paths.get(response.path)));
        createdBackups.add(Paths.get(response.path));
    }

    @Test
    void shouldRejectRestoreForMissingFile() {
        RestAssured.given()
            .queryParam("path", "/nonexistent/path/backup.zip")
            .when()
            .post("/api/admin/database/restore")
            .then()
            .statusCode(400)
            .body(containsString("Backup file does not exist"));
    }

    @Test
    void shouldRejectRestoreForInvalidZip() throws IOException {
        Path temp = Files.createTempFile("not-a-backup", ".zip");
        try {
            Files.write(temp, "not a zip".getBytes());
            RestAssured.given()
                .queryParam("path", temp.toString())
                .when()
                .post("/api/admin/database/restore")
                .then()
                .statusCode(400)
                .body(containsString("Invalid H2 backup file"));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    @Transactional
    void shouldStageRestoreFile() throws IOException {
        entityManager.createNativeQuery("SELECT 1").getSingleResult();

        DatabaseAdminApiTest.BackupResponse backup = RestAssured.given()
            .queryParam("name", "restore_source.zip")
            .when()
            .post("/api/admin/database/backup")
            .then()
            .statusCode(200)
            .extract()
            .as(DatabaseAdminApiTest.BackupResponse.class);

        assertTrue(backup.success);
        assertNotNull(backup.path);
        assertTrue(Files.exists(Paths.get(backup.path)));
        createdBackups.add(Paths.get(backup.path));

        DatabaseAdminApiTest.RestoreResponse response = RestAssured.given()
            .queryParam("path", backup.path)
            .when()
            .post("/api/admin/database/restore")
            .then()
            .statusCode(200)
            .extract()
            .as(DatabaseAdminApiTest.RestoreResponse.class);

        assertNotNull(response.stagedPath);
        assertTrue(response.restartCommand.contains("unzip"));
        assertTrue(Files.exists(Paths.get(response.stagedPath)));
    }

    public record BackupResponse(String path, boolean success) {
    }

    public record RestoreResponse(String stagedPath, String restartCommand) {
    }

    public static class FileDatabaseProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quarkus.datasource.jdbc.url", "jdbc:h2:file:./target/test-db/database;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
            );
        }
    }
}
