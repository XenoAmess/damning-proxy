[中文版](03-plugin-system.md)

# 03 Plugin System

> Last updated: 2026-06-17  
> Corresponding source version: current workspace

## What Plugins Can Do

A plugin is a Groovy or JavaScript script executed during the proxy request or response phase. It can:

- Read/modify the request body and request headers
- Read/modify the response body, response headers, and response status code
- Record logs
- Stop subsequent plugin execution
- Return a response directly, skipping the upstream request

---

## Plugin Context API

Plugin scripts operate on the request/response via the `context` object.

Full class: `src/main/java/com/xenoamess/damning_proxy/plugin/PluginContext.java`

| Method/Property | Description |
|---|---|
| `context.getRequestBody()` / `context.setRequestBody(obj)` | Read/write request body |
| `context.getRequestHeaders()` / `context.getRequestHeader(key)` / `context.setRequestHeader(key, value)` | Read/write request headers |
| `context.getResponseBody()` / `context.setResponseBody(obj)` | Read/write response body |
| `context.getResponseHeaders()` / `context.getResponseHeader(key)` / `context.setResponseHeader(key, value)` | Read/write response headers |
| `context.getResponseStatus()` / `context.setResponseStatus(code)` | Read/write response status code |
| `context.log(message)` | Record plugin log |
| `context.stop()` | Stop subsequent plugin execution |
| `context.isStopped()` | Whether already stopped |
| `context.returnResponse(status, body, headers)` | Return response directly, skip upstream |
| `context.isReturned()` | Whether already returned via returnResponse |

---

## Plugin Execution Phase

`src/main/java/com/xenoamess/damning_proxy/entity/Plugin.java:54`

| Phase | Description |
|---|---|
| `REQUEST` | Execute only in the request phase |
| `RESPONSE` | Execute only in the response phase |
| `BOTH` | Execute in both request and response phases |

The same `BOTH` plugin executes once in the request phase and once in the response phase. The script can determine the current phase by checking fields such as `context.getResponseStatus()`.

---

## Plugin Execution Service

`src/main/java/com/xenoamess/damning_proxy/plugin/PluginExecutionService.java:11`

Execution rules:

1. Iterate plugins in `PluginGroup.sortedItems()` order.
2. Skip disabled plugins or group items.
3. Stop subsequent plugins if `context.isStopped()` or `context.isReturned()`.
4. Match the plugin's `executionPhase` based on the current phase (REQUEST / RESPONSE).
5. Find the corresponding language `PluginEngine` to execute the script.
6. Capture exceptions, record them to logs and the friendly snapshot, without interrupting the pipeline.

`PluginExecutionSnapshot` is generated before and after execution, recording plugin name, phase, input, output, and error information.

---

## Plugin Engines

### Groovy Engine

`src/main/java/com/xenoamess/damning_proxy/plugin/engine/GroovyPluginEngine.java:15`

```text
GroovyShell shell
scriptCache: Map<String, Script>   // Cached by script content

execute(plugin, context)
  ├─ Get or parse Script from cache
  ├─ Binding binding
  ├─ binding.setVariable("context", context)
  ├─ script.setBinding(binding)
  └─ script.run()
```

### JavaScript Engine

`src/main/java/com/xenoamess/damning_proxy/plugin/engine/JavaScriptPluginEngine.java:15`

```text
sourceCache: Map<String, Source>   // Cached by script content

execute(plugin, context)
  ├─ Get or build Source from cache
  ├─ Context.newBuilder("js").allowAllAccess(true).build()
  ├─ bindings.putMember("context", context)
  └─ jsContext.eval(source)
```

### Cache Notes

- Both Groovy and JS engines cache compiled scripts by `plugin.script` string content.
- **Cache is not automatically invalidated after modifying a plugin script**; the service must be restarted for changes to take effect (or cache invalidation logic must be implemented).

---

## Plugin Examples

### JS: Rewrite Request Model Name

```javascript
var body = context.getRequestBody();
body.model = 'gpt-4o';
context.setRequestBody(body);
context.log('rewrote model to gpt-4o');
```

### Groovy: Return a Custom Response Directly

```groovy
context.returnResponse(200, [message: 'intercepted'], ['X-Custom': 'yes'])
```

More examples can be found in [05-guides/02-plugin-development.en.md](../05-guides/02-plugin-development.en.md).

---

## Plugin Execution Flow Diagram

```text
Request enters
  │
  ▼
┌─────────────────────────────┐
│ Execute REQUEST / BOTH phase │
│ plugins in orderIndex →      │
│ priority → id order          │
└─────────────────────────────┘
  │
  ├─ Plugin calls context.returnResponse()
  │     └─ Skip upstream, return directly as response
  │
  ├─ Plugin calls context.stop()
  │     └─ Stop subsequent plugins, but continue forwarding to upstream
  │
  └─ All plugins executed
        │
        ▼
  Forward to upstream OpenAI source
        │
        ▼
┌─────────────────────────────┐
│ Execute RESPONSE / BOTH phase│
│ plugins in orderIndex →      │
│ priority → id order          │
└─────────────────────────────┘
        │
        ▼
  Return to client
```

For streaming responses, response-phase plugins are executed after the complete SSE stream is received, operating on the accumulated content.
