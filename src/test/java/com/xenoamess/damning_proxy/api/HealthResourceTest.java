package com.xenoamess.damning_proxy.api;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.xenoamess.damning_proxy.entity.ProxyProfile;
import com.xenoamess.damning_proxy.repository.ProfileRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class HealthResourceTest {

    WireMockServer wireMockServer;

    @Inject
    ProfileRepository profileRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        wireMockServer = new WireMockServer(18089);
        wireMockServer.start();
        profileRepository.listAll().forEach(p -> profileRepository.deleteById(p.id));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldReportHealthyWithReachableUpstream() {
        wireMockServer.stubFor(get(urlEqualTo("/v1/models"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"object\":\"list\",\"data\":[]}")));

        ProxyProfile profile = new ProxyProfile("OpenAI", "openai", "http://localhost:18089/v1");
        profile.enabled = true;
        saveProfile(profile);

        given()
            .when().get("/v1/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("ok"))
            .body("database", equalTo("ok"))
            .body("upstreams.openai.status", equalTo("up"))
            .body("upstreams.openai.statusCode", equalTo(200));
    }

    @Test
    void shouldReportDegradedWithUnreachableUpstream() {
        ProxyProfile profile = new ProxyProfile("Down", "down", "http://localhost:18090/v1");
        profile.enabled = true;
        saveProfile(profile);

        given()
            .when().get("/v1/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("degraded"))
            .body("upstreams.down.status", equalTo("down"));
    }

    @Test
    void shouldReportDisabledUpstream() {
        wireMockServer.stubFor(get(urlEqualTo("/v1/models"))
            .willReturn(aResponse().withStatus(404)));

        ProxyProfile profile = new ProxyProfile("Disabled", "disabled", "http://localhost:18089/v1");
        profile.enabled = false;
        saveProfile(profile);

        given()
            .when().get("/v1/health")
            .then()
            .statusCode(200)
            .body("upstreams.disabled.status", equalTo("disabled"))
            .body("upstreams.disabled.enabled", equalTo(false));
    }

    @Transactional
    ProxyProfile saveProfile(ProxyProfile profile) {
        profileRepository.save(profile);
        return profile;
    }
}
