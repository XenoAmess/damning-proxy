package com.xenoamess.damning_proxy.plugin;

import com.xenoamess.damning_proxy.entity.Plugin;
import com.xenoamess.damning_proxy.plugin.engine.GroovyPluginEngine;
import com.xenoamess.damning_proxy.plugin.engine.JavaScriptPluginEngine;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PluginEngineTest {

    @Test
    void groovyShouldModifyRequestHeader() {
        GroovyPluginEngine engine = new GroovyPluginEngine();
        Plugin plugin = new Plugin();
        plugin.language = Plugin.Language.GROOVY;
        plugin.script = "context.requestHeaders.put('X-Custom', 'groovy-value')";

        PluginContext context = new PluginContext();
        engine.execute(plugin, context);

        assertEquals("groovy-value", context.getRequestHeaders().get("X-Custom"));
    }

    @Test
    void groovyShouldModifyRequestBody() {
        GroovyPluginEngine engine = new GroovyPluginEngine();
        Plugin plugin = new Plugin();
        plugin.language = Plugin.Language.GROOVY;
        plugin.script = """
            def body = context.requestBody
            body.model = 'modified-model'
            context.requestBody = body
            """;

        PluginContext context = new PluginContext();
        Map<String, Object> body = new HashMap<>();
        body.put("model", "original");
        context.setRequestBody(body);

        engine.execute(plugin, context);

        Map<String, Object> result = (Map<String, Object>) context.getRequestBody();
        assertEquals("modified-model", result.get("model"));
    }

    @Test
    void groovyShouldSupportStopAndReturnResponse() {
        GroovyPluginEngine engine = new GroovyPluginEngine();
        Plugin plugin = new Plugin();
        plugin.language = Plugin.Language.GROOVY;
        plugin.script = """
            context.returnResponse(418, [message: 'I am a teapot'], ['X-Teapot': 'yes'])
            """;

        PluginContext context = new PluginContext();
        engine.execute(plugin, context);

        assertTrue(context.isReturned());
        assertEquals(418, context.getResponseStatus());
        assertEquals("yes", context.getResponseHeaders().get("X-Teapot"));
    }

    @Test
    void javaScriptShouldModifyRequestHeader() {
        JavaScriptPluginEngine engine = new JavaScriptPluginEngine();
        Plugin plugin = new Plugin();
        plugin.id = 1L;
        plugin.language = Plugin.Language.JS;
        plugin.script = "context.setRequestHeader('X-Custom', 'js-value')";

        PluginContext context = new PluginContext();
        engine.execute(plugin, context);

        assertEquals("js-value", context.getRequestHeader("X-Custom"));
    }

    @Test
    void javaScriptShouldModifyResponseBody() {
        JavaScriptPluginEngine engine = new JavaScriptPluginEngine();
        Plugin plugin = new Plugin();
        plugin.id = 2L;
        plugin.language = Plugin.Language.JS;
        plugin.script = """
            var body = context.getResponseBody();
            body.put('model', 'js-modified');
            context.setResponseBody(body);
            context.log('modified by js');
            """;

        PluginContext context = new PluginContext();
        Map<String, Object> body = new HashMap<>();
        body.put("model", "original");
        context.setResponseBody(body);

        engine.execute(plugin, context);

        Map<String, Object> result = (Map<String, Object>) context.getResponseBody();
        assertEquals("js-modified", result.get("model"));
        assertTrue(context.getPluginLogs().contains("modified by js"));
    }

    @Test
    void javaScriptShouldSupportStop() {
        JavaScriptPluginEngine engine = new JavaScriptPluginEngine();
        Plugin plugin = new Plugin();
        plugin.id = 3L;
        plugin.language = Plugin.Language.JS;
        plugin.script = "context.stop()";

        PluginContext context = new PluginContext();
        engine.execute(plugin, context);

        assertTrue(context.isStopped());
    }

    @Test
    void groovyShouldBlockSandboxedClassInstantiation() {
        GroovyPluginEngine engine = new GroovyPluginEngine();
        Plugin plugin = new Plugin();
        plugin.language = Plugin.Language.GROOVY;
        plugin.script = "new File('/etc/passwd').text";

        PluginContext context = new PluginContext();
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> engine.execute(plugin, context));
        assertTrue(thrown.getMessage().contains("not allowed") || thrown.getMessage().contains("SecurityException"),
            thrown.getMessage());
    }

    @Test
    void groovyShouldBlockSandboxedImport() {
        GroovyPluginEngine engine = new GroovyPluginEngine();
        Plugin plugin = new Plugin();
        plugin.language = Plugin.Language.GROOVY;
        plugin.script = """
            import java.net.URL
            new URL('http://example.com')
            """;

        PluginContext context = new PluginContext();
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> engine.execute(plugin, context));
        assertTrue(thrown.getMessage().contains("not allowed") || thrown.getMessage().contains("SecurityException"),
            thrown.getMessage());
    }

    @Test
    void javaScriptShouldBlockSandboxedJavaType() {
        JavaScriptPluginEngine engine = new JavaScriptPluginEngine();
        Plugin plugin = new Plugin();
        plugin.id = 4L;
        plugin.name = "test";
        plugin.language = Plugin.Language.JS;
        plugin.script = "var File = Java.type('java.io.File'); new File('/etc/passwd');";

        PluginContext context = new PluginContext();
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> engine.execute(plugin, context));
        assertSandboxBlocked(thrown, "java.io.File");
    }

    @Test
    void javaScriptShouldBlockSandboxedDirectClassAccess() {
        JavaScriptPluginEngine engine = new JavaScriptPluginEngine();
        Plugin plugin = new Plugin();
        plugin.id = 5L;
        plugin.name = "test";
        plugin.language = Plugin.Language.JS;
        plugin.script = "new java.io.File('/etc/passwd');";

        PluginContext context = new PluginContext();
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> engine.execute(plugin, context));
        assertSandboxBlocked(thrown, "java.io.File");
    }

    private void assertSandboxBlocked(RuntimeException thrown, String className) {
        String message = thrown.getMessage();
        Throwable cause = thrown.getCause();
        while (cause != null) {
            if (cause.getMessage() != null) {
                message += " " + cause.getMessage();
            }
            cause = cause.getCause();
        }
        assertTrue(message.contains(className) || message.contains("ClassNotFoundException") || message.contains("ClassFilter"),
            message);
    }
}
