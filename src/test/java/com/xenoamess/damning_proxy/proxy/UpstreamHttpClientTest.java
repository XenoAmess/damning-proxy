package com.xenoamess.damning_proxy.proxy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.xenoamess.damning_proxy.entity.ProxyProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.MultiMap;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestProfile(UpstreamHttpClientTest.RetryEnabledProfile.class)
class UpstreamHttpClientTest {

    @Inject
    UpstreamHttpClient upstreamHttpClient;

    WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(18089);
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldSendRequestAndReturnResponse() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":true}")));

        ProxyProfile profile = profile();
        UpstreamHttpClient.UpstreamResponse response = upstreamHttpClient.send(
            "POST", "http://localhost:18089/v1", "/chat/completions",
            MultiMap.caseInsensitiveMultiMap(), Map.of("model", "gpt-4"), 5000, profile
        );

        assertEquals(200, response.statusCode);
        assertEquals("{\"ok\":true}", response.body);
        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void shouldThrowGatewayTimeoutOnSlowUpstream() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withFixedDelay(500)
                .withStatus(200)));

        ProxyProfile profile = profile();
        WebApplicationException ex = assertThrows(WebApplicationException.class, () ->
            upstreamHttpClient.send(
                "POST", "http://localhost:18089/v1", "/chat/completions",
                MultiMap.caseInsensitiveMultiMap(), Map.of("model", "gpt-4"), 100, profile
            )
        );

        assertEquals(504, ex.getResponse().getStatus());
    }

    @Test
    void shouldRetryOn502AndReturn200() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .inScenario("Retry502")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(502).withBody("bad gateway"))
            .willSetStateTo("second"));

        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .inScenario("Retry502")
            .whenScenarioStateIs("second")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":true}")));

        ProxyProfile profile = profile();
        UpstreamHttpClient.UpstreamResponse response = upstreamHttpClient.send(
            "POST", "http://localhost:18089/v1", "/chat/completions",
            MultiMap.caseInsensitiveMultiMap(), Map.of("model", "gpt-4"), 5000, profile
        );

        assertEquals(200, response.statusCode);
        assertEquals("{\"ok\":true}", response.body);
        wireMockServer.verify(2, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void shouldNotRetryOn400() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse().withStatus(400).withBody("bad request")));

        ProxyProfile profile = profile();
        UpstreamHttpClient.UpstreamResponse response = upstreamHttpClient.send(
            "POST", "http://localhost:18089/v1", "/chat/completions",
            MultiMap.caseInsensitiveMultiMap(), Map.of("model", "gpt-4"), 5000, profile
        );

        assertEquals(400, response.statusCode);
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void shouldReturnBadGatewayAfterMaxRetries() {
        String errorBody = "{\"error\":\"upstream failure\"}";
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse().withStatus(502).withBody(errorBody)));

        ProxyProfile profile = profile();
        UpstreamHttpClient.UpstreamResponse response = upstreamHttpClient.send(
            "POST", "http://localhost:18089/v1", "/chat/completions",
            MultiMap.caseInsensitiveMultiMap(), Map.of("model", "gpt-4"), 5000, profile
        );

        assertEquals(502, response.statusCode);
        assertEquals(errorBody, response.body);
        wireMockServer.verify(3, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void shouldRetryOnTimeout() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .inScenario("TimeoutRetry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withFixedDelay(500).withStatus(200))
            .willSetStateTo("second"));

        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .inScenario("TimeoutRetry")
            .whenScenarioStateIs("second")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":true}")));

        ProxyProfile profile = profile();
        UpstreamHttpClient.UpstreamResponse response = upstreamHttpClient.send(
            "POST", "http://localhost:18089/v1", "/chat/completions",
            MultiMap.caseInsensitiveMultiMap(), Map.of("model", "gpt-4"), 100, profile
        );

        assertEquals(200, response.statusCode);
        assertEquals("{\"ok\":true}", response.body);
        wireMockServer.verify(2, postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    private ProxyProfile profile() {
        ProxyProfile profile = new ProxyProfile("test", "test", "http://localhost:18089/v1");
        profile.circuitBreakerFailureThreshold = 1000;
        return profile;
    }

    public static class RetryEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "damning-proxy.upstream.max-retries", "2",
                "damning-proxy.upstream.retry-base-delay-ms", "10",
                "damning-proxy.upstream.retry-max-delay-ms", "50"
            );
        }
    }
}
