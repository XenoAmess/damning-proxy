[中文版](02-plugin-development.md)

# 02 Plugin Development Guide

> Last updated: 2026-07-07  
> Source version: current workspace

## Plugin Basics

A plugin is a Groovy or JavaScript script that runs during the request/response phases of the proxy. The script accesses and modifies requests/responses through the global variable `context`.

For the full API, see [02-design/03-plugin-system.md](../02-design/03-plugin-system.en.md).

---

## context API Quick Reference

| Method | Description |
|---|---|
| `context.getRequestBody()` / `context.setRequestBody(obj)` | Read/write request body |
| `context.getRequestHeaders()` / `context.setRequestHeader(k, v)` | Read/write request headers |
| `context.getResponseBody()` / `context.setResponseBody(obj)` | Read/write response body |
| `context.getResponseHeaders()` / `context.setResponseHeader(k, v)` | Read/write response headers |
| `context.getResponseStatus()` / `context.setResponseStatus(code)` | Read/write response status code |
| `context.log(message)` | Log a plugin message |
| `context.stop()` | Stop subsequent plugin execution |
| `context.returnResponse(status, body, headers)` | Return a response directly, skipping the upstream |

---

## Execution Phases

When creating a plugin, select the execution phase:

- `REQUEST`: executes only during the request phase
- `RESPONSE`: executes only during the response phase
- `STREAM_CHUNK`: executes on each SSE chunk of a streaming response; can modify or filter individual chunks in real time
- `BOTH`: executes during both request and response phases

`STREAM_CHUNK` plugins run every time an SSE chunk is received. If the plugin calls `context.returnResponse(...)`, that chunk is not sent to the client (commonly used for content filtering); if `context.getResponseBody()` is modified, the modified chunk is sent.

---

## Plugin Sandbox

Plugin scripts run inside a sandbox by default. Dangerous APIs such as file, network, and reflection access are blocked to reduce the risk of running untrusted scripts.

### Default Denied Classes / Packages

| Category | Examples |
|----------|----------|
| File IO | `java.io.*` (`File`, `FileInputStream`, etc.) |
| Network | `java.net.*` (`URL`, `Socket`, `URLConnection`, etc.) |
| NIO file | `java.nio.file.*` (`Files`, `Paths`, etc.) |
| Reflection | `java.lang.reflect.*` |
| Process / runtime | `java.lang.Runtime`, `java.lang.ProcessBuilder` |

Groovy scripts are intercepted at compile time by `SecureASTCustomizer` (imports and receiver calls). JavaScript scripts use the Nashorn `ClassFilter` to prevent access to blocked Java classes.

### Configuration

In `application.properties`:

```properties
# Disable the sandbox (not recommended)
damning-proxy.plugin.sandbox.enabled=false

# Add extra denied classes or packages
damning-proxy.plugin.sandbox.denied-classes=java.sql.DriverManager
damning-proxy.plugin.sandbox.denied-packages=java.sql
```

---

## Revision History and Rollback

Every time a single-script plugin is saved, the current script content is automatically saved as a revision. Open the plugin editor and look at the **Revision History** panel to:

- **Preview**: load the historical script into the editor for review
- **Rollback**: restore the plugin script to the selected revision (the current script is snapshotted before rollback, so the rollback itself can be undone)

Revisions are cleaned up when the plugin is deleted.

Backend endpoints:

- `GET /api/plugins/{id}/revisions` — list revisions for a plugin
- `POST /api/plugins/{id}/revisions/{revisionId}/rollback` — roll back to a specific revision

---

## JavaScript Plugin Examples

### Rewrite the request model name

```javascript
var body = context.getRequestBody();
body.model = 'gpt-4o';
context.setRequestBody(body);
context.log('rewrote model to gpt-4o');
```

### Append a system prompt

```javascript
const body = context.getRequestBody();
if (!body || !Array.isArray(body.messages)) return;

const hint = 'Please answer in concise English.';
const systemMessage = body.messages.find(m => m && m.role === 'system');

if (systemMessage && typeof systemMessage.content === 'string') {
  systemMessage.content += '\n' + hint;
} else {
  body.messages.unshift({ role: 'system', content: hint });
}
context.log('appended system hint');
```

### Return a response directly (mock)

```javascript
context.returnResponse(200, {
  id: 'mock',
  model: 'gpt-4o',
  choices: [{
    message: { role: 'assistant', content: 'This content is returned directly by the plugin.' }
  }]
}, { 'X-Mocked': 'true' });
```

---

## Groovy Plugin Examples

### Rewrite the request model name

```groovy
def body = context.getRequestBody()
body.model = 'gpt-4o'
context.setRequestBody(body)
context.log('rewrote model to gpt-4o')
```

### Append a system prompt

```groovy
def body = context.getRequestBody()
if (body == null) return

def messages = body.get("messages")
if (!(messages instanceof List)) return

def hint = "Please answer in concise English."
def systemMessage = null

for (def m : messages) {
  if (m == null) continue
  if ("system".equals(m.get("role"))) {
    systemMessage = m
    break
  }
}

if (systemMessage != null) {
  def content = systemMessage.get("content")
  if (content instanceof String) {
    systemMessage.put("content", content + "\n" + hint)
  }
} else {
  def newSystem = new LinkedHashMap()
  newSystem.put("role", "system")
  newSystem.put("content", hint)
  messages.add(0, newSystem)
}
context.log('appended system hint')
```

### Return a response directly

```groovy
context.returnResponse(200,
  [id: 'mock', model: 'gpt-4o', choices: [[message: [role: 'assistant', content: 'Groovy mocked']]]],
  ['X-Mocked': 'true'])
```

---

## Response-Phase Plugins

In the response phase, plugins can modify content returned by the upstream, e.g., replacing the model name:

```javascript
var body = context.getResponseBody();
if (body && body.model) {
  body.model = 'custom-model';
  context.setResponseBody(body);
  context.log('rewrote response model');
}
```

---

## Plugin Group Configuration

1. Go to the **Plugin Groups** page.
2. Create a plugin group and select the plugins to combine.
3. Adjust `orderIndex` and `priority`.
4. In **Instance Management**, bind the instance to that plugin group.

Execution order: `orderIndex` ascending → `priority` ascending → `id` ascending.

---

## Debug Plugins

1. Use `context.log('...')` in the script to output debug information.
2. Make a proxy request.
3. Go to **Traffic Logs** → click details → view **Plugin Pipeline** and **Plugin Logs**.

---

## Notes

- Plugin scripts are cached by content hash; after saving changes, the next request automatically recompiles and uses the new script. No service restart is required.
- Plugins have full JVM permissions; do not run scripts from untrusted sources.
- Response-phase plugins operate on the accumulated complete response body for streaming requests; use the `STREAM_CHUNK` phase to process each SSE chunk in real time.
- If a plugin throws an exception, the pipeline is not interrupted, but the error is recorded in the friendly snapshot.

---

## Built-in Sample Plugins

Two sample plugins are created automatically on startup:

- `大明战锤提示词（Groovy）`
- `大明战锤提示词（JS）`

Both append a fixed system prompt to the message list during the request phase.

Source code: `src/main/java/com/xenoamess/damning_proxy/migration/StartupMigration.java:113`
