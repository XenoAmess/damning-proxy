package com.xenoamess.damning_proxy.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.entity.TrafficLog;
import com.xenoamess.damning_proxy.repository.LogRepository;
import com.xenoamess.damning_proxy.repository.PluginGroupRepository;
import com.xenoamess.damning_proxy.repository.PluginRepository;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

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

    @BeforeEach
    @Transactional
    void setUp() {
        profileRepository.listAll().forEach(p -> profileRepository.deleteById(p.id));
        pluginGroupRepository.listAll().forEach(g -> pluginGroupRepository.deleteById(g.id));
        pluginRepository.listAll().forEach(p -> pluginRepository.deleteById(p.id));
        logRepository.deleteAll();

        TrafficLog log = new TrafficLog();
        log.requestPath = "/v1/chat/completions";
        log.requestMethod = "POST";
        logRepository.save(log);
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
    void shouldCrudPlugins() {
        Plugin plugin = new Plugin();
        plugin.name = "TestPlugin";
        plugin.language = Plugin.Language.GROOVY;
        plugin.script = "context.stop()";

        given()
            .contentType(ContentType.JSON)
            .body(plugin)
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
            .when().delete("/api/plugins/" + id)
            .then()
            .statusCode(204);

        assertTrue(pluginRepository.listAll().isEmpty());
    }

    @Test
    void shouldCrudLogs() {
        given()
            .when().get("/api/logs")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1));

        Long id = logRepository.listRecent(1).get(0).id;

        given()
            .when().get("/api/logs/" + id)
            .then()
            .statusCode(200)
            .body("id", equalTo(id.intValue()));

        given()
            .when().post("/api/logs/clear")
            .then()
            .statusCode(200)
            .body("deleted", greaterThanOrEqualTo(1));

        assertEquals(0, logRepository.listRecent(100).size());
    }

    @Test
    void shouldCrudPluginGroups() {
        Plugin plugin = new Plugin();
        plugin.name = "GroupTestPlugin";
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
            .when().delete("/api/plugin-groups/" + id)
            .then()
            .statusCode(204);

        assertTrue(pluginGroupRepository.listAll().isEmpty());
    }

    @Test
    void shouldListSamplePluginsAndGroupsAfterStartup() {
        assertEquals(2, pluginRepository.listAll().size());
        assertEquals(2, pluginGroupRepository.listAll().size());

        given()
            .when().get("/api/plugins")
            .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .body("name", hasItems("大明战锤提示词（Groovy）", "大明战锤提示词（JS）"));

        given()
            .when().get("/api/plugin-groups")
            .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .body("slug", hasItems("sample-groovy", "sample-js"));
    }
}
