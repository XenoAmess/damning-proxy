package com.xenoamess.damning_proxy.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.xenoamess.damning_proxy.entity.PluginGroup;
import com.xenoamess.damning_proxy.entity.ProxyInstance;
import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.entity.TrafficLog;
import com.xenoamess.damning_proxy.repository.InstanceRepository;
import com.xenoamess.damning_proxy.repository.LogRepository;
import com.xenoamess.damning_proxy.repository.PluginGroupRepository;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ProxyApiTest {

    WireMockServer wireMockServer;

    @Inject
    ProfileRepository profileRepository;

    @Inject
    PluginGroupRepository pluginGroupRepository;

    @Inject
    InstanceRepository instanceRepository;

    @Inject
    LogRepository logRepository;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    @Transactional
    void setUp() {
        wireMockServer = new WireMockServer(18089);
        wireMockServer.start();
        instanceRepository.listAll().forEach(i -> instanceRepository.deleteById(i.id));
        pluginGroupRepository.listAll().forEach(g -> pluginGroupRepository.deleteById(g.id));
        profileRepository.listAll().forEach(p -> profileRepository.deleteById(p.id));
        logRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldReturn404WhenInstanceNotFound() {
        given()
            .when().get("/v1/proxy/nonexistent/models")
            .then()
            .statusCode(404);
    }

    @Test
    void shouldReturn403WhenInstanceDisabled() {
        ProxyProfile profile = saveProfile(new ProxyProfile("Disabled", "disabled", "http://localhost:18089"));
        PluginGroup group = saveGroup(new PluginGroup());
        ProxyInstance instance = createInstance("disabled", profile.id, group.id, false);

        given()
            .when().get("/v1/proxy/disabled/models")
            .then()
            .statusCode(403);
    }

    @Test
    void shouldProxyListModels() {
        wireMockServer.stubFor(get(urlEqualTo("/v1/models"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"object\":\"list\",\"data\":[{\"id\":\"gpt-4\",\"object\":\"model\"}]}")));

        ProxyInstance instance = createInstance("openai", "http://localhost:18089/v1", "sk-test");

        given()
            .when().get("/v1/proxy/openai/models")
            .then()
            .statusCode(200)
            .body("object", equalTo("list"))
            .body("data.size()", equalTo(1))
            .body("data[0].id", equalTo("gpt-4"));

        wireMockServer.verify(getRequestedFor(urlEqualTo("/v1/models"))
            .withHeader("Authorization", WireMock.equalTo("Bearer sk-test")));

        TrafficLog log = logRepository.listRecent(1).get(0);
        assertEquals("/v1/models", log.requestPath);
        assertEquals(200, log.responseStatus);
        assertNotNull(log.responseBody);
        assertEquals(instance.id, log.instanceId);
    }

    @Test
    void shouldProxyChatCompletions() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"model\":\"gpt-4\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hi\"}}]}")));

        createInstance("openai-chat", "http://localhost:18089/v1", "sk-test");

        Map<String, Object> body = Map.of(
            "model", "gpt-4",
            "messages", java.util.List.of(Map.of("role", "user", "content", "Hello")),
            "stream", false
        );

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/v1/proxy/openai-chat/chat/completions")
            .then()
            .statusCode(200)
            .body("id", equalTo("chatcmpl-1"))
            .body("model", equalTo("gpt-4"))
            .body("choices[0].message.content", equalTo("Hi"));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
            .withHeader("Authorization", WireMock.equalTo("Bearer sk-test")));

        TrafficLog log = logRepository.listRecent(1).get(0);
        assertEquals("/v1/chat/completions", log.requestPath);
        assertEquals(200, log.responseStatus);
        assertNotNull(log.requestBody);
        assertNotNull(log.responseBody);
    }

    @Test
    void shouldProxyWithCustomHeaders() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":true}")));

        ProxyProfile profile = new ProxyProfile("Custom", "custom", "http://localhost:18089/v1");
        profile.customHeaders = "{\"X-Api-Key\":\"secret\",\"X-Project\":\"damning\"}";
        profile = saveProfile(profile);
        PluginGroup group = saveGroup(new PluginGroup());
        ProxyInstance instance = createInstance("custom", profile.id, group.id, true);

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("model", "gpt-4", "messages", java.util.List.of()))
            .when().post("/v1/proxy/custom/chat/completions")
            .then()
            .statusCode(200);

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
            .withHeader("X-Api-Key", WireMock.equalTo("secret"))
            .withHeader("X-Project", WireMock.equalTo("damning")));
    }

    @Transactional
    ProxyProfile saveProfile(ProxyProfile profile) {
        profileRepository.save(profile);
        return profile;
    }

    @Transactional
    PluginGroup saveGroup(PluginGroup group) {
        group.name = "Test Group";
        group.slug = "test-group-" + System.nanoTime();
        group.enabled = true;
        pluginGroupRepository.save(group);
        return group;
    }

    @Transactional
    ProxyInstance createInstance(String slug, String baseUrl, String token) {
        ProxyProfile profile = new ProxyProfile(slug, slug, baseUrl);
        profile.bearerToken = token;
        profileRepository.save(profile);
        return createInstance(slug, profile.id, saveGroup(new PluginGroup()).id, true);
    }

    @Transactional
    ProxyInstance createInstance(String slug, Long profileId, Long groupId, boolean enabled) {
        ProxyInstance instance = new ProxyInstance();
        instance.name = slug;
        instance.slug = slug;
        instance.profileId = profileId;
        instance.pluginGroupId = groupId;
        instance.enabled = enabled;
        instanceRepository.save(instance);
        return instance;
    }
}
