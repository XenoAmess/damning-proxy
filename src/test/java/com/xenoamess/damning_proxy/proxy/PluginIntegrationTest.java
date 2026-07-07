package com.xenoamess.damning_proxy.proxy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.entity.PluginGroup;
import com.xenoamess.damning_proxy.entity.PluginGroupItem;
import com.xenoamess.damning_proxy.entity.ProxyInstance;
import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.repository.InstanceRepository;
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

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PluginIntegrationTest {

    @Inject
    ProfileRepository profileRepository;
    @Inject
    PluginGroupRepository pluginGroupRepository;
    @Inject
    PluginRepository pluginRepository;
    @Inject
    InstanceRepository instanceRepository;

    WireMockServer wireMockServer;

    @BeforeEach
    @Transactional
    void setUp() {
        wireMockServer = new WireMockServer(18089);
        wireMockServer.start();
        instanceRepository.listAll().forEach(i -> instanceRepository.deleteById(i.id));
        pluginGroupRepository.listAll().forEach(g -> pluginGroupRepository.deleteById(g.id));
        pluginRepository.listAll().forEach(p -> pluginRepository.deleteById(p.id));
        profileRepository.listAll().forEach(p -> profileRepository.deleteById(p.id));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldApplyRequestPhasePlugin() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"1\",\"model\":\"gpt-4\"}")));

        ProxyProfile profile = saveProfile("openai", "http://localhost:18089/v1", "sk-test");
        Plugin plugin = savePlugin("req-modifier",
            "const body = context.getRequestBody(); body.model = 'modified'; context.setRequestBody(body);",
            Plugin.ExecutionPhase.REQUEST);
        PluginGroup group = saveGroupWithPlugin("group-req", plugin);
        saveInstance("instance-req", profile.id, group.id);

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("model", "gpt-4", "messages", List.of()))
            .when().post("/v1/proxy/instance-req/chat/completions")
            .then()
            .statusCode(200);

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
            .withRequestBody(equalToJson("{\"model\":\"modified\",\"messages\":[]}")));
    }

    @Test
    void shouldApplyResponsePhasePlugin() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"1\",\"model\":\"gpt-4\",\"choices\":[]}")));

        ProxyProfile profile = saveProfile("openai", "http://localhost:18089/v1", "sk-test");
        Plugin plugin = savePlugin("resp-modifier",
            "const body = context.getResponseBody(); body.model = 'modified'; context.setResponseBody(body);",
            Plugin.ExecutionPhase.RESPONSE);
        PluginGroup group = saveGroupWithPlugin("group-resp", plugin);
        saveInstance("instance-resp", profile.id, group.id);

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("model", "gpt-4", "messages", List.of()))
            .when().post("/v1/proxy/instance-resp/chat/completions")
            .then()
            .statusCode(200)
            .body("model", equalTo("modified"));
    }

    @Test
    void shouldApplyStreamChunkPhasePlugin() {
        String sseBody = "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"A\"}}]}\n\n" +
            "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"B\"}}]}\n\n" +
            "data: [DONE]\n\n";
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withChunkedDribbleDelay(5, 100)
                .withBody(sseBody)));

        ProxyProfile profile = saveProfile("openai", "http://localhost:18089/v1", "sk-test");
        Plugin plugin = savePlugin("chunk-modifier",
            "const body = context.getResponseBody(); " +
                "if (body.choices && body.choices[0] && body.choices[0].delta && body.choices[0].delta.content === 'A') { " +
                "  body.choices[0].delta.content = 'X'; context.setResponseBody(body); " +
                "} else if (body.choices && body.choices[0] && body.choices[0].delta && body.choices[0].delta.content === 'B') { " +
                "  context.returnResponse(200, {}, {}); " +
                "}",
            Plugin.ExecutionPhase.STREAM_CHUNK);
        PluginGroup group = saveGroupWithPlugin("group-chunk", plugin);
        saveInstance("instance-chunk", profile.id, group.id);

        Map<String, Object> body = Map.of(
            "model", "gpt-4",
            "messages", List.of(Map.of("role", "user", "content", "Hello")),
            "stream", true
        );

        String response = given()
            .contentType(ContentType.JSON)
            .header("Accept", "text/event-stream")
            .body(body)
            .when().post("/v1/proxy/instance-chunk/chat/completions")
            .then()
            .statusCode(200)
            .header("Content-Type", containsString("text/event-stream"))
            .extract().body().asString();

        assertTrue(response.contains("X"), response);
        assertFalse(response.contains("\"content\":\"A\""), response);
        assertFalse(response.contains("\"content\":\"B\""), response);
    }

    @Transactional
    ProxyProfile saveProfile(String slug, String baseUrl, String token) {
        ProxyProfile p = new ProxyProfile(slug, slug, baseUrl);
        p.bearerToken = token;
        profileRepository.save(p);
        return p;
    }

    @Transactional
    Plugin savePlugin(String slug, String script, Plugin.ExecutionPhase phase) {
        Plugin p = new Plugin();
        p.name = slug;
        p.slug = slug;
        p.language = Plugin.Language.JS;
        p.mode = Plugin.Mode.SINGLE_SCRIPT;
        p.executionPhase = phase;
        p.script = script;
        p.enabled = true;
        pluginRepository.save(p);
        return p;
    }

    @Transactional
    PluginGroup saveGroupWithPlugin(String slug, Plugin plugin) {
        PluginGroup g = new PluginGroup();
        g.name = slug;
        g.slug = slug;
        g.enabled = true;
        PluginGroupItem item = new PluginGroupItem();
        item.group = g;
        item.plugin = plugin;
        item.orderIndex = 0;
        item.priority = 0;
        item.enabled = true;
        g.items.add(item);
        pluginGroupRepository.save(g);
        return g;
    }

    @Transactional
    ProxyInstance saveInstance(String slug, Long profileId, Long groupId) {
        ProxyInstance i = new ProxyInstance();
        i.name = slug;
        i.slug = slug;
        i.profileId = profileId;
        i.pluginGroupId = groupId;
        i.enabled = true;
        instanceRepository.save(i);
        return i;
    }
}
