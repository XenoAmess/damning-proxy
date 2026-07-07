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
import jakarta.persistence.EntityManager;
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
import static org.hamcrest.Matchers.containsString;
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

    @Inject
    EntityManager entityManager;

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
                .withHeader("Content-Encoding", "identity")
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
    void shouldProxyStreamingChatCompletions() throws InterruptedException {
        String sseBody = "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"Hi\"}}]}\n\ndata: [DONE]\n\n";
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withChunkedDribbleDelay(5, 100)
                .withBody(sseBody)));

        ProxyInstance instance = createInstance("openai-stream", "http://localhost:18089/v1", "sk-test");

        Map<String, Object> body = Map.of(
            "model", "gpt-4",
            "messages", java.util.List.of(Map.of("role", "user", "content", "Hello")),
            "stream", true
        );

        String response = given()
            .contentType(ContentType.JSON)
            .header("Accept", "text/event-stream")
            .body(body)
            .when().post("/v1/proxy/openai-stream/chat/completions")
            .then()
            .statusCode(200)
            .header("Content-Type", containsString("text/event-stream"))
            .extract().body().asString();

        assertTrue(response.contains("data: "));
        assertTrue(response.contains("chatcmpl-1"));
        assertTrue(response.contains("Hi"));

        TrafficLog log = waitForLogByInstance(instance.id);
        assertEquals("/v1/chat/completions", log.requestPath);
        assertEquals(200, log.responseStatus);
        assertTrue(log.streaming);
        assertNotNull(log.responseBody);
    }

    @Test
    void shouldReturnSseErrorOnUpstreamStreamingFailure() throws InterruptedException {
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"message\":\"Internal Server Error\"}}")));

        ProxyInstance instance = createInstance("openai-stream-err", "http://localhost:18089/v1", "sk-test");

        Map<String, Object> body = Map.of(
            "model", "gpt-4",
            "messages", java.util.List.of(Map.of("role", "user", "content", "Hello")),
            "stream", true
        );

        String response = given()
            .contentType(ContentType.JSON)
            .header("Accept", "text/event-stream")
            .body(body)
            .when().post("/v1/proxy/openai-stream-err/chat/completions")
            .then()
            .statusCode(200)
            .header("Content-Type", containsString("text/event-stream"))
            .extract().body().asString();

        assertTrue(response.contains("event: error"), response);
        assertTrue(response.contains("Internal Server Error"), response);

        TrafficLog log = waitForLogByInstance(instance.id);
        assertEquals("/v1/chat/completions", log.requestPath);
        assertEquals(500, log.responseStatus);
        assertTrue(log.streaming);
    }

    private TrafficLog waitForLogByInstance(Long instanceId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            entityManager.clear();
            TrafficLog latest = null;
            for (TrafficLog log : logRepository.findByInstanceId(instanceId, 10)) {
                if (log.responseBody != null && (latest == null || log.id > latest.id)) {
                    latest = log;
                }
            }
            if (latest != null) {
                return latest;
            }
            Thread.sleep(100);
        }
        entityManager.clear();
        TrafficLog latest = null;
        for (TrafficLog log : logRepository.findByInstanceId(instanceId, 10)) {
            if (latest == null || log.id > latest.id) {
                latest = log;
            }
        }
        return latest;
    }

    @Test
    void shouldProxyEmbeddings() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/embeddings"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"embedding\":[0.1,0.2],\"index\":0}],\"model\":\"text-embedding-3-small\",\"usage\":{\"prompt_tokens\":2,\"total_tokens\":2}}")));

        createInstance("openai-embeddings", "http://localhost:18089/v1", "sk-test");

        Map<String, Object> body = Map.of(
            "model", "text-embedding-3-small",
            "input", "hello"
        );

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/v1/proxy/openai-embeddings/embeddings")
            .then()
            .statusCode(200)
            .body("object", equalTo("list"))
            .body("data.size()", equalTo(1))
            .body("model", equalTo("text-embedding-3-small"));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/embeddings"))
            .withHeader("Authorization", WireMock.equalTo("Bearer sk-test")));
    }

    @Test
    void shouldProxyImageGenerations() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/images/generations"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"created\":1234567890,\"data\":[{\"url\":\"http://example.com/image.png\"}]}")));

        createInstance("openai-images", "http://localhost:18089/v1", "sk-test");

        Map<String, Object> body = Map.of(
            "model", "dall-e-3",
            "prompt", "a cat",
            "n", 1,
            "size", "1024x1024"
        );

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/v1/proxy/openai-images/images/generations")
            .then()
            .statusCode(200)
            .body("data.size()", equalTo(1))
            .body("data[0].url", equalTo("http://example.com/image.png"));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/images/generations"))
            .withHeader("Authorization", WireMock.equalTo("Bearer sk-test")));
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
