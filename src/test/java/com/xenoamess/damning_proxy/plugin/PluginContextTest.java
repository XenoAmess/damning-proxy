package com.xenoamess.damning_proxy.plugin;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PluginContextTest {

    @Test
    void shouldGetAndSetRequestBody() {
        PluginContext ctx = new PluginContext();
        Map<String, Object> body = Map.of("model", "gpt-4");
        ctx.setRequestBody(body);
        assertEquals(body, ctx.getRequestBody());
    }

    @Test
    void shouldGetAndSetRequestHeader() {
        PluginContext ctx = new PluginContext();
        ctx.setRequestHeader("Authorization", "Bearer test");
        assertEquals("Bearer test", ctx.getRequestHeaders().get("Authorization"));
    }

    @Test
    void shouldGetAndSetResponseBody() {
        PluginContext ctx = new PluginContext();
        Map<String, Object> body = Map.of("choices", java.util.List.of());
        ctx.setResponseBody(body);
        assertEquals(body, ctx.getResponseBody());
    }

    @Test
    void shouldGetAndSetResponseStatus() {
        PluginContext ctx = new PluginContext();
        ctx.setResponseStatus(404);
        assertEquals(404, ctx.getResponseStatus());
    }

    @Test
    void shouldStopExecution() {
        PluginContext ctx = new PluginContext();
        assertFalse(ctx.isStopped());
        ctx.stop();
        assertTrue(ctx.isStopped());
    }

    @Test
    void shouldReturnResponse() {
        PluginContext ctx = new PluginContext();
        assertFalse(ctx.isReturned());
        Map<String, String> headers = Map.of("X-Custom", "value");
        ctx.returnResponse(200, Map.of("ok", true), headers);
        assertTrue(ctx.isReturned());
        assertEquals(200, ctx.getResponseStatus());
        assertEquals("value", ctx.getResponseHeaders().get("X-Custom"));
    }

    @Test
    void shouldLogPluginMessages() {
        PluginContext ctx = new PluginContext();
        ctx.log("first message");
        ctx.log("second message");
        assertEquals(2, ctx.getPluginLogs().size());
        assertEquals("first message", ctx.getPluginLogs().get(0));
    }

    @Test
    void shouldResetChunkState() {
        PluginContext ctx = new PluginContext();
        ctx.stop();
        ctx.returnResponse(200, Map.of("ok", true), Map.of());
        assertTrue(ctx.isStopped());
        assertTrue(ctx.isReturned());
        ctx.resetChunkState();
        assertFalse(ctx.isStopped());
        assertFalse(ctx.isReturned());
    }

    @Test
    void shouldBeThreadSafeForConcurrentLogging() throws Exception {
        PluginContext ctx = new PluginContext();
        int threads = 4;
        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            workers[t] = new Thread(() -> {
                for (int i = 0; i < 100; i++) {
                    ctx.log("msg " + i);
                }
            });
            workers[t].start();
        }
        for (Thread t : workers) {
            t.join();
        }
        assertEquals(400, ctx.getPluginLogs().size());
    }
}
