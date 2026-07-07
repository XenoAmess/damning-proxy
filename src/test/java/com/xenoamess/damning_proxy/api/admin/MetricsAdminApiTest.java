package com.xenoamess.damning_proxy.api.admin;

import com.xenoamess.damning_proxy.entity.TrafficLog;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MetricsAdminApiTest {

    @BeforeEach
    @Transactional
    void setUp() {
        TrafficLog.deleteAll();
    }

    @Test
    void shouldReturnSummaryMetrics() {
        createLogs();

        Map<String, Object> summary = RestAssured.given()
            .when()
            .get("/api/metrics/summary")
            .then()
            .statusCode(200)
            .body("totalRequests", is(2))
            .body("errorRequests", is(1))
            .extract()
            .as(Map.class);

        assertNotNull(summary.get("avgLatencyMs"));
        assertNotNull(summary.get("totalTokens"));
    }

    @Test
    void shouldReturnTimeSeries() {
        createLogs();

        List<Map<String, Object>> series = RestAssured.given()
            .when()
            .get("/api/metrics/time-series")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1))
            .extract()
            .as(List.class);

        Map<String, Object> first = series.get(0);
        assertNotNull(first.get("bucket"));
        assertNotNull(first.get("requests"));
        assertNotNull(first.get("tokens"));
        assertNotNull(first.get("errors"));
    }

    @Test
    void shouldReturnTopInstances() {
        createLogs();
        createLog("beta", 200, 100, 0, 0, 0);

        List<Map<String, Object>> top = RestAssured.given()
            .when()
            .get("/api/metrics/top-instances")
            .then()
            .statusCode(200)
            .body("size()", is(2))
            .extract()
            .as(List.class);

        assertEquals("alpha", top.get(0).get("instanceSlug"));
        assertEquals(2, ((Number) top.get(0).get("requests")).intValue());
    }

    @Test
    void shouldReturnStatusDistribution() {
        createLogs();

        List<Map<String, Object>> dist = RestAssured.given()
            .when()
            .get("/api/metrics/status-distribution")
            .then()
            .statusCode(200)
            .body("size()", is(2))
            .extract()
            .as(List.class);

        assertTrue(dist.stream().anyMatch(m -> "success".equals(m.get("status")) && ((Number) m.get("count")).intValue() == 1));
        assertTrue(dist.stream().anyMatch(m -> "error".equals(m.get("status")) && ((Number) m.get("count")).intValue() == 1));
    }

    @Transactional
    void createLogs() {
        createLog("alpha", 200, 100, 10, 20, 30);
        createLog("alpha", 500, 200, 0, 0, 0);
    }

    @Transactional
    void createLog(String slug, int status, long duration, int prompt, int completion, int total) {
        TrafficLog log = new TrafficLog();
        log.instanceSlug = slug;
        log.requestMethod = "POST";
        log.requestPath = "/v1/chat/completions";
        log.requestTime = LocalDateTime.now();
        log.responseStatus = status;
        log.durationMs = duration;
        log.promptTokens = prompt;
        log.completionTokens = completion;
        log.totalTokens = total;
        log.persist();
    }
}
