package com.xenoamess.damning_proxy.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.entity.TrafficLog;
import com.xenoamess.damning_proxy.repository.InstanceRepository;
import com.xenoamess.damning_proxy.repository.LogRepository;
import com.xenoamess.damning_proxy.repository.PluginGroupRepository;
import com.xenoamess.damning_proxy.repository.PluginRepository;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import jakarta.persistence.EntityManager;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AdminApiTest {

    @Inject
    ProfileRepository profileRepository;

    @Inject
    PluginRepository pluginRepository;

    @Inject
    LogRepository logRepository;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    PluginGroupRepository pluginGroupRepository;

    @Inject
    InstanceRepository instanceRepository;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    @Transactional
    void setUp() {
        logRepository.deleteAll();
        instanceRepository.listAll().forEach(i -> instanceRepository.deleteById(i.id));
        profileRepository.listAll().forEach(p -> profileRepository.deleteById(p.id));
        pluginGroupRepository.listAll().forEach(g -> pluginGroupRepository.deleteById(g.id));
        pluginRepository.listAll().forEach(p -> pluginRepository.deleteById(p.id));

        TrafficLog log = new TrafficLog();
        log.requestPath = "/v1/chat/completions";
        log.requestMethod = "POST";
        logRepository.save(log);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        logRepository.deleteAll();
        instanceRepository.listAll().forEach(i -> instanceRepository.deleteById(i.id));
        profileRepository.listAll().forEach(p -> profileRepository.deleteById(p.id));
        pluginGroupRepository.listAll().forEach(g -> pluginGroupRepository.deleteById(g.id));
        pluginRepository.listAll().forEach(p -> pluginRepository.deleteById(p.id));
    }

    @Test
    void shouldCrudProfiles() {
        ProxyProfile profile = new ProxyProfile("Test", "test", "http://localhost:1234");
        profile.bearerToken = "sk-test";

        given()
            .contentType(ContentType.JSON)
            .body(profile)
            .when().post("/api/profiles")
            .then()
            .statusCode(201)
            .body("slug", equalTo("test"));

        given()
            .when().get("/api/profiles")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1));

        Long id = profileRepository.findBySlug("test").map(p -> p.id).orElseThrow();

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "Updated", "slug", "test", "baseUrl", "http://x"))
            .when().put("/api/profiles/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("Updated"));

        given()
            .when().delete("/api/profiles/" + id)
            .then()
            .statusCode(204);

        assertTrue(profileRepository.findBySlug("test").isEmpty());
    }

    @Test
    void shouldPersistCircuitBreakerFieldsOnProfileUpdate() {
        ProxyProfile profile = new ProxyProfile("Cb", "cb", "http://localhost:1234");

        given()
            .contentType(ContentType.JSON)
            .body(profile)
            .when().post("/api/profiles")
            .then()
            .statusCode(201);

        Long id = profileRepository.findBySlug("cb").map(p -> p.id).orElseThrow();

        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "name", "Cb",
                "slug", "cb",
                "baseUrl", "http://localhost:1234",
                "timeoutMs", 600000,
                "circuitBreakerFailureThreshold", 5,
                "circuitBreakerOpenTimeoutSeconds", 60,
                "enabled", true
            ))
            .when().put("/api/profiles/" + id)
            .then()
            .statusCode(200)
            .body("circuitBreakerFailureThreshold", equalTo(5))
            .body("circuitBreakerOpenTimeoutSeconds", equalTo(60));

        entityManager.clear();
        ProxyProfile updated = profileRepository.findById(id).orElseThrow();
        assertEquals(Integer.valueOf(5), updated.circuitBreakerFailureThreshold);
        assertEquals(Integer.valueOf(60), updated.circuitBreakerOpenTimeoutSeconds);
    }

    @Test
    void shouldCrudPlugins() {
        Plugin plugin = new Plugin();
        plugin.name = "TestPlugin";
        plugin.language = Plugin.Language.GROOVY;
        plugin.script = "context.stop()";

        // /api/plugins is @Consumes(MULTIPART_FORM_DATA) — the production
        // admin UI uploads scripts that way too. Use multipart here.
        given()
            .multiPart("name", plugin.name)
            .multiPart("slug", "test-plugin")
            .multiPart("language", plugin.language.name())
            .multiPart("executionPhase", Plugin.ExecutionPhase.REQUEST.name())
            .multiPart("mode", Plugin.Mode.SINGLE_SCRIPT.name())
            .multiPart("script", plugin.script)
            .multiPart("enabled", "true")
            .when().post("/api/plugins")
            .then()
            .statusCode(201)
            .body("name", equalTo("TestPlugin"));

        given()
            .when().get("/api/plugins")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1));

        Long id = pluginRepository.listAll().get(0).id;

        given()
            .multiPart("name", "UpdatedPlugin")
            .multiPart("slug", "test-plugin")
            .multiPart("language", Plugin.Language.JS.name())
            .multiPart("executionPhase", Plugin.ExecutionPhase.RESPONSE.name())
            .multiPart("mode", Plugin.Mode.SINGLE_SCRIPT.name())
            .multiPart("script", "context.log('updated')")
            .multiPart("enabled", "true")
            .when().put("/api/plugins/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("UpdatedPlugin"))
            .body("language", equalTo("JS"));

        given()
            .when().delete("/api/plugins/" + id)
            .then()
            .statusCode(204);

        assertTrue(pluginRepository.listAll().isEmpty());
    }

    @Test
    void shouldRejectPluginWithInvalidScript() {
        given()
            .multiPart("name", "InvalidGroovy")
            .multiPart("slug", "invalid-groovy")
            .multiPart("language", Plugin.Language.GROOVY.name())
            .multiPart("executionPhase", Plugin.ExecutionPhase.REQUEST.name())
            .multiPart("mode", Plugin.Mode.SINGLE_SCRIPT.name())
            .multiPart("script", "def x = { // missing closure end")
            .multiPart("enabled", "true")
            .when().post("/api/plugins")
            .then()
            .statusCode(400)
            .body(containsString("Groovy compile error"));

        given()
            .multiPart("name", "InvalidJs")
            .multiPart("slug", "invalid-js")
            .multiPart("language", Plugin.Language.JS.name())
            .multiPart("executionPhase", Plugin.ExecutionPhase.REQUEST.name())
            .multiPart("mode", Plugin.Mode.SINGLE_SCRIPT.name())
            .multiPart("script", "function foo() { return { // missing braces")
            .multiPart("enabled", "true")
            .when().post("/api/plugins")
            .then()
            .statusCode(400)
            .body(containsString("JavaScript"));
    }

    @Test
    void shouldRejectPluginUpdateWithInvalidScript() {
        Long id = given()
            .multiPart("name", "UpdatePlugin")
            .multiPart("slug", "update-plugin")
            .multiPart("language", Plugin.Language.GROOVY.name())
            .multiPart("executionPhase", Plugin.ExecutionPhase.REQUEST.name())
            .multiPart("mode", Plugin.Mode.SINGLE_SCRIPT.name())
            .multiPart("script", "context.log('ok')")
            .multiPart("enabled", "true")
            .when().post("/api/plugins")
            .then()
            .statusCode(201)
            .extract().jsonPath().getLong("id");

        given()
            .multiPart("name", "UpdatePlugin")
            .multiPart("slug", "update-plugin")
            .multiPart("language", Plugin.Language.GROOVY.name())
            .multiPart("executionPhase", Plugin.ExecutionPhase.REQUEST.name())
            .multiPart("mode", Plugin.Mode.SINGLE_SCRIPT.name())
            .multiPart("script", "def broken = { // syntax error")
            .multiPart("enabled", "true")
            .when().put("/api/plugins/" + id)
            .then()
            .statusCode(400)
            .body(containsString("Groovy compile error"));
    }

    @Test
    void shouldDryRunPlugin() {
        Long id = given()
            .multiPart("name", "DryRunPlugin")
            .multiPart("slug", "dry-run-plugin")
            .multiPart("language", Plugin.Language.GROOVY.name())
            .multiPart("executionPhase", Plugin.ExecutionPhase.REQUEST.name())
            .multiPart("mode", Plugin.Mode.SINGLE_SCRIPT.name())
            .multiPart("script", "def body = context.getRequestBody(); body.dry = true; context.setRequestBody(body);")
            .multiPart("enabled", "true")
            .when().post("/api/plugins")
            .then()
            .statusCode(201)
            .extract().jsonPath().getLong("id");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "phase", "REQUEST",
                "requestBody", Map.of("messages", java.util.List.of())
            ))
            .when().post("/api/plugins/" + id + "/dry-run")
            .then()
            .statusCode(200)
            .body("pluginName", equalTo("DryRunPlugin"))
            .body("phase", equalTo("REQUEST"))
            .body("requestBody.dry", equalTo(true))
            .body("error", equalTo(false));
    }

    @Test
    void shouldCrudInstances() {
        Long profileId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "CrudProfile", "slug", "crud-profile", "baseUrl", "http://localhost:1234"))
            .when().post("/api/profiles")
            .then()
            .statusCode(201)
            .extract().jsonPath().getLong("id");

        Long pluginId = given()
            .multiPart("name", "CrudPlugin")
            .multiPart("slug", "crud-plugin")
            .multiPart("language", "GROOVY")
            .multiPart("executionPhase", "REQUEST")
            .multiPart("mode", "SINGLE_SCRIPT")
            .multiPart("script", "context.log('crud')")
            .multiPart("enabled", "true")
            .when().post("/api/plugins")
            .then()
            .statusCode(201)
            .extract().jsonPath().getLong("id");

        Long groupId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "CrudGroup", "slug", "crud-group", "description", "", "enabled", true,
                "items", java.util.List.of(Map.of("pluginId", pluginId, "orderIndex", 0, "priority", 0, "enabled", true))))
            .when().post("/api/plugin-groups")
            .then()
            .statusCode(201)
            .extract().jsonPath().getLong("id");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "CrudInstance", "slug", "crud-instance",
                "profileId", profileId, "pluginGroupId", groupId,
                "defaultModel", "gpt-4", "enabled", true))
            .when().post("/api/instances")
            .then()
            .statusCode(201)
            .body("slug", equalTo("crud-instance"));

        given()
            .when().get("/api/instances")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1));

        Long instanceId = instanceRepository.listAll().get(0).id;

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "UpdatedInstance", "slug", "crud-instance",
                "profileId", profileId, "pluginGroupId", groupId,
                "defaultModel", "gpt-3.5-turbo", "enabled", false))
            .when().put("/api/instances/" + instanceId)
            .then()
            .statusCode(200)
            .body("name", equalTo("UpdatedInstance"))
            .body("enabled", equalTo(false));

        given()
            .when().delete("/api/instances/" + instanceId)
            .then()
            .statusCode(204);

        assertTrue(instanceRepository.listAll().isEmpty());
    }

    @Test
    @Transactional
    void shouldCrudLogs() {
        Long id = logRepository.listRecent(1).get(0).id;

        given()
            .when().get("/api/logs")
            .then()
            .statusCode(200)
            .body("items.size()", equalTo(1));

        given()
            .when().get("/api/logs/" + id)
            .then()
            .statusCode(200)
            .body("id", equalTo(id.intValue()));

        given()
            .when().get("/api/logs/" + id + "/friendly")
            .then()
            .statusCode(200)
            .body("id", equalTo(id.intValue()));

        given()
            .when().delete("/api/logs/" + id)
            .then()
            .statusCode(204);

        entityManager.clear();
        assertTrue(logRepository.findById(id).isEmpty());
    }

    @Test
    @Transactional
    void shouldClearLogs() {
        given()
            .when().post("/api/logs/clear")
            .then()
            .statusCode(200)
            .body("deleted", greaterThanOrEqualTo(1));

        assertEquals(0, logRepository.listRecent(100).size());
    }

    @Test
    @Transactional
    void shouldCrudPluginGroups() {
        Plugin plugin = new Plugin();
        plugin.name = "GroupTestPlugin";
        plugin.slug = "group-test-plugin";
        plugin.language = Plugin.Language.GROOVY;
        plugin.script = "context.stop()";
        pluginRepository.save(plugin);

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "TestGroup", "slug", "test-group", "description", "desc", "enabled", true,
                "items", java.util.List.of(Map.of("pluginId", plugin.id, "orderIndex", 0, "priority", 0, "enabled", true))))
            .when().post("/api/plugin-groups")
            .then()
            .statusCode(201)
            .body("slug", equalTo("test-group"));

        given()
            .when().get("/api/plugin-groups")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1));

        Long id = pluginGroupRepository.listAll().get(0).id;

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "UpdatedGroup", "slug", "test-group", "description", "updated", "enabled", false,
                "items", java.util.List.of(Map.of("pluginId", plugin.id, "orderIndex", 1, "priority", 1, "enabled", false))))
            .when().put("/api/plugin-groups/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("UpdatedGroup"))
            .body("enabled", equalTo(false));

        given()
            .when().delete("/api/plugin-groups/" + id)
            .then()
            .statusCode(204);

        assertTrue(pluginGroupRepository.listAll().isEmpty());
    }

    @Test
    void shouldListSamplePluginsAndGroupsAfterStartup() {
        given()
            .multiPart("name", "Sample Groovy Plugin")
            .multiPart("slug", "groovy-user-test")
            .multiPart("language", "GROOVY")
            .multiPart("executionPhase", "REQUEST")
            .multiPart("mode", "SINGLE_SCRIPT")
            .multiPart("script", "context.log('groovy')")
            .multiPart("enabled", "true")
            .when().post("/api/plugins")
            .then()
            .statusCode(201)
            .body("sample", equalTo(false));

        given()
            .multiPart("name", "Sample JS Plugin")
            .multiPart("slug", "js-user-test")
            .multiPart("language", "JS")
            .multiPart("executionPhase", "REQUEST")
            .multiPart("mode", "SINGLE_SCRIPT")
            .multiPart("script", "context.log('js')")
            .multiPart("enabled", "true")
            .when().post("/api/plugins")
            .then()
            .statusCode(201)
            .body("sample", equalTo(false));

        given()
            .when().get("/api/plugins")
            .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .body("name", hasItems("Sample Groovy Plugin", "Sample JS Plugin"))
            .body("sample", hasItems(false, false));
    }

    @Test
    void shouldExportAndImportPlugins() {
        given()
            .multiPart("name", "ExportTest")
            .multiPart("slug", "export-test")
            .multiPart("language", "GROOVY")
            .multiPart("executionPhase", "REQUEST")
            .multiPart("mode", "SINGLE_SCRIPT")
            .multiPart("script", "context.log('export-test')")
            .multiPart("enabled", "true")
            .when().post("/api/plugins")
            .then()
            .statusCode(201)
            .body("name", equalTo("ExportTest"));

        Long id = pluginRepository.listAll().get(0).id;

        // /api/plugins/export produces an application/zip download (matches what the
        // admin UI imports); verify only the status and content-type here.
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("ids", java.util.List.of(id)))
            .when().post("/api/plugins/export")
            .then()
            .statusCode(200)
            .contentType("application/zip");

        given()
            .contentType(ContentType.JSON)
            .body(java.util.List.of(Map.of(
                "name", "ImportTest",
                "slug", "import-test",
                "description", "",
                "language", "GROOVY",
                "executionPhase", "REQUEST",
                "mode", "SINGLE_SCRIPT",
                "script", "context.log('import-test')",
                "enabled", true
            )))
            .when().post("/api/plugins/import")
            .then()
            .statusCode(200)
            .body("imported", equalTo(1))
            .body("skipped", equalTo(0));

        given()
            .contentType(ContentType.JSON)
            .body(java.util.List.of(Map.of(
                "name", "ImportTestDup",
                "slug", "import-test",
                "description", "",
                "language", "GROOVY",
                "executionPhase", "REQUEST",
                "mode", "SINGLE_SCRIPT",
                "script", "context.log('import-test')",
                "enabled", true
            )))
            .when().post("/api/plugins/import")
            .then()
            .statusCode(200)
            .body("imported", equalTo(0))
            .body("skipped", equalTo(1));

        given()
            .when().get("/api/plugins")
            .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .body("sample", hasItems(false, false));
    }

    @Test
    void shouldExportAndImportPluginGroups() {
        Long pluginId = given()
            .multiPart("name", "GroupExportPlugin")
            .multiPart("slug", "group-export-plugin")
            .multiPart("language", "GROOVY")
            .multiPart("executionPhase", "REQUEST")
            .multiPart("mode", "SINGLE_SCRIPT")
            .multiPart("script", "context.log('group-export')")
            .multiPart("enabled", "true")
            .when().post("/api/plugins")
            .then()
            .statusCode(201)
            .extract().jsonPath().getLong("id");

        Long groupId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "ExportGroup", "slug", "export-group", "description", "desc", "enabled", true,
                "items", java.util.List.of(Map.of("pluginId", pluginId, "orderIndex", 0, "priority", 0, "enabled", true))))
            .when().post("/api/plugin-groups")
            .then()
            .statusCode(201)
            .body("slug", equalTo("export-group"))
            .extract().jsonPath().getLong("id");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("ids", java.util.List.of(groupId)))
            .when().post("/api/plugin-groups/export")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].slug", equalTo("export-group"))
            .body("[0].items.size()", equalTo(1))
            .body("[0].items[0].pluginScript", equalTo("context.log('group-export')"));

        given()
            .contentType(ContentType.JSON)
            .body(java.util.List.of(Map.of(
                "name", "ImportGroup",
                "slug", "import-group",
                "description", "desc",
                "enabled", true,
                "items", java.util.List.of(Map.of(
                    "pluginScript", "context.log('group-export')",
                    "orderIndex", 0,
                    "priority", 0,
                    "enabled", true
                ))
            )))
            .when().post("/api/plugin-groups/import")
            .then()
            .statusCode(200)
            .body("imported", equalTo(1))
            .body("skipped", equalTo(0));

        given()
            .contentType(ContentType.JSON)
            .body(java.util.List.of(Map.of(
                "name", "ImportGroupDup",
                "slug", "import-group",
                "description", "desc",
                "enabled", true,
                "items", java.util.List.of(Map.of(
                    "pluginScript", "context.log('group-export')",
                    "orderIndex", 0,
                    "priority", 0,
                    "enabled", true
                ))
            )))
            .when().post("/api/plugin-groups/import")
            .then()
            .statusCode(200)
            .body("imported", equalTo(0))
            .body("skipped", equalTo(1));

        given()
            .when().get("/api/plugin-groups")
            .then()
            .statusCode(200)
            .body("size()", equalTo(2));
    }

    @Test
    void shouldExportAndImportInstances() {
        Long profileId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "ExportProfile", "slug", "export-profile", "baseUrl", "http://localhost:1234"))
            .when().post("/api/profiles")
            .then()
            .statusCode(201)
            .extract().jsonPath().getLong("id");

        Long pluginId = given()
            .multiPart("name", "InstanceExportPlugin")
            .multiPart("slug", "instance-export-plugin")
            .multiPart("language", "GROOVY")
            .multiPart("executionPhase", "REQUEST")
            .multiPart("mode", "SINGLE_SCRIPT")
            .multiPart("script", "context.log('instance-export')")
            .multiPart("enabled", "true")
            .when().post("/api/plugins")
            .then()
            .statusCode(201)
            .extract().jsonPath().getLong("id");

        Long groupId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "ExportGroupForInstance", "slug", "export-group-for-instance", "description", "", "enabled", true,
                "items", java.util.List.of(Map.of("pluginId", pluginId, "orderIndex", 0, "priority", 0, "enabled", true))))
            .when().post("/api/plugin-groups")
            .then()
            .statusCode(201)
            .extract().jsonPath().getLong("id");

        Long instanceId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "ExportInstance", "slug", "export-instance",
                "profileId", profileId, "pluginGroupId", groupId,
                "defaultModel", "gpt-4", "enabled", true))
            .when().post("/api/instances")
            .then()
            .statusCode(201)
            .body("slug", equalTo("export-instance"))
            .extract().jsonPath().getLong("id");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("ids", java.util.List.of(instanceId)))
            .when().post("/api/instances/export")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].slug", equalTo("export-instance"))
            .body("[0].profileSlug", equalTo("export-profile"))
            .body("[0].pluginGroupSlug", equalTo("export-group-for-instance"));

        given()
            .contentType(ContentType.JSON)
            .body(java.util.List.of(Map.of(
                "name", "ImportInstance",
                "slug", "import-instance",
                "profileSlug", "export-profile",
                "pluginGroupSlug", "export-group-for-instance",
                "defaultModel", "gpt-4",
                "enabled", true
            )))
            .when().post("/api/instances/import")
            .then()
            .statusCode(200)
            .body("imported", equalTo(1))
            .body("skipped", equalTo(0));

        given()
            .contentType(ContentType.JSON)
            .body(java.util.List.of(Map.of(
                "name", "ImportInstanceDup",
                "slug", "import-instance",
                "profileSlug", "export-profile",
                "pluginGroupSlug", "export-group-for-instance",
                "defaultModel", "gpt-4",
                "enabled", true
            )))
            .when().post("/api/instances/import")
            .then()
            .statusCode(200)
            .body("imported", equalTo(0))
            .body("skipped", equalTo(1));

        given()
            .when().get("/api/instances")
            .then()
            .statusCode(200)
            .body("size()", equalTo(2));
    }
}
