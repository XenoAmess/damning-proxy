package com.xenoamess.badass_model.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class OpenAiCompatibleApiTest {

    private static final String TEST_TOKEN = "sk-badass-model-demo-token";

    @Test
    public void testHealthEndpoint() {
        given()
            .when().get("/v1/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("ok"));
    }

    @Test
    public void testModelsWithoutAuth() {
        given()
            .when().get("/v1/models")
            .then()
            .statusCode(401);
    }

    @Test
    public void testModelsWithAuth() {
        given()
            .header("Authorization", "Bearer " + TEST_TOKEN)
            .when().get("/v1/models")
            .then()
            .statusCode(200)
            .body("object", equalTo("list"))
            .body("data.size()", equalTo(3))
            .body("data[0].id", notNullValue());
    }

    @Test
    public void testChatCompletionsWithoutAuth() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"model\": \"mock-gpt-4o\", \"messages\": [{\"role\": \"user\", \"content\": \"Hello\"}], \"stream\": true}")
            .when().post("/v1/chat/completions")
            .then()
            .statusCode(401);
    }

    @Test
    public void testChatCompletionsWithAuth() {
        String body = given()
            .header("Authorization", "Bearer " + TEST_TOKEN)
            .contentType(ContentType.JSON)
            .body("{\"model\": \"mock-gpt-4o\", \"messages\": [{\"role\": \"user\", \"content\": \"Hello\"}], \"stream\": true}")
            .when().post("/v1/chat/completions")
            .then()
            .statusCode(200)
            .contentType("text/event-stream")
            .extract().asString();

        // REST Assured normalizes SSE newlines, so we just verify the body contains expected content
        assertNotNull(body);
        assertTrue(body.contains("data:"), "Response should contain SSE data prefix");
        assertTrue(body.contains("\"object\":\"chat.completion.chunk\""), "Response should contain chat completion chunks");
        assertTrue(body.contains("\"role\":\"assistant\""), "Response should contain assistant role");
    }

    @Test
    public void testChatCompletionsMissingMessages() {
        given()
            .header("Authorization", "Bearer " + TEST_TOKEN)
            .contentType(ContentType.JSON)
            .body("{\"model\": \"mock-gpt-4o\", \"stream\": true}")
            .when().post("/v1/chat/completions")
            .then()
            .statusCode(400);
    }

    @Test
    public void testInvalidToken() {
        given()
            .header("Authorization", "Bearer invalid-token")
            .when().get("/v1/models")
            .then()
            .statusCode(401);
    }
}
