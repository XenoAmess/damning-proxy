package com.xenoamess.damning_proxy.proxy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.xenoamess.damning_proxy.entity.PluginGroup;
import com.xenoamess.damning_proxy.entity.ProxyInstance;
import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.repository.InstanceRepository;
import com.xenoamess.damning_proxy.repository.PluginGroupRepository;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ProxyApiAudioTest {

    WireMockServer wireMockServer;

    @Inject
    ProfileRepository profileRepository;

    @Inject
    PluginGroupRepository pluginGroupRepository;

    @Inject
    InstanceRepository instanceRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        wireMockServer = new WireMockServer(18090);
        wireMockServer.start();
        instanceRepository.listAll().forEach(i -> instanceRepository.deleteById(i.id));
        pluginGroupRepository.listAll().forEach(g -> pluginGroupRepository.deleteById(g.id));
        profileRepository.listAll().forEach(p -> profileRepository.deleteById(p.id));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldProxyAudioSpeech() {
        byte[] audioBytes = new byte[] { 0x52, 0x49, 0x46, 0x46 }; // WAV header prefix
        wireMockServer.stubFor(post(urlEqualTo("/v1/audio/speech"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "audio/wav")
                .withBody(audioBytes)));

        createInstance("audio", "http://localhost:18090/v1", "sk-test");

        Map<String, Object> body = Map.of(
            "model", "tts-1",
            "input", "Hello",
            "voice", "alloy"
        );

        byte[] response = given()
            .contentType("application/json")
            .body(body)
            .when().post("/v1/proxy/audio/audio/speech")
            .then()
            .statusCode(200)
            .contentType("audio/wav")
            .extract()
            .asByteArray();

        assertArrayEquals(audioBytes, response);
        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/audio/speech"))
            .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo("Bearer sk-test")));
    }

    @Test
    void shouldProxyAudioTranscriptions() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/v1/audio/transcriptions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"text\":\"Hello world\"}")));

        createInstance("audio-stt", "http://localhost:18090/v1", "sk-test");

        File temp = File.createTempFile("audio", ".wav");
        try {
            Files.write(temp.toPath(), new byte[] { 0x52, 0x49, 0x46, 0x46 });

            given()
                .multiPart("file", temp)
                .multiPart("model", "whisper-1")
                .multiPart("language", "en")
                .when().post("/v1/proxy/audio-stt/audio/transcriptions")
                .then()
                .statusCode(200)
                .body("text", equalTo("Hello world"));

            wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/audio/transcriptions"))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo("Bearer sk-test")));
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    @Test
    void shouldProxyAudioTranslations() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/v1/audio/translations"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"text\":\"Hola mundo\"}")));

        createInstance("audio-trans", "http://localhost:18090/v1", "sk-test");

        File temp = File.createTempFile("audio", ".mp3");
        try {
            Files.write(temp.toPath(), new byte[] { 0x49, 0x44, 0x33 });

            given()
                .multiPart("file", temp)
                .multiPart("model", "whisper-1")
                .when().post("/v1/proxy/audio-trans/audio/translations")
                .then()
                .statusCode(200)
                .body("text", equalTo("Hola mundo"));

            wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/audio/translations"))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo("Bearer sk-test")));
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    @Transactional
    ProxyInstance createInstance(String slug, String baseUrl, String token) {
        ProxyProfile profile = new ProxyProfile(slug, slug, baseUrl);
        profile.bearerToken = token;
        profileRepository.save(profile);

        PluginGroup group = new PluginGroup();
        group.name = "Test Group";
        group.slug = "test-group-" + System.nanoTime();
        group.enabled = true;
        pluginGroupRepository.save(group);

        ProxyInstance instance = new ProxyInstance();
        instance.name = slug;
        instance.slug = slug;
        instance.profileId = profile.id;
        instance.pluginGroupId = group.id;
        instance.enabled = true;
        instanceRepository.save(instance);
        return instance;
    }
}
