[中文版](04-self-test.md)

# 04 Self-Testing

> Last updated: 2026-06-17  
> Source version: current workspace

## Unit Tests and Integration Tests

### Run All Tests

```bash
mvn test
```

- Executes all JUnit 5 tests under `src/test/java`.
- Uses in-memory H2, `src/test/resources/application.properties`.
- Integration tests use WireMock to simulate the upstream OpenAI service.
- **Requires JDK 21**; other versions may cause Byte Buddy / GraalJS initialization failures.

---

## Test File List

| Test Class | Coverage | File |
|---|---|---|
| `AdminApiTest` | Admin CRUD (Profile/Plugin/Group/Log) | `src/test/java/com/xenoamess/damning_proxy/api/admin/AdminApiTest.java` |
| `ProxyApiTest` | Proxy forwarding, custom headers, traffic log | `src/test/java/com/xenoamess/damning_proxy/proxy/ProxyApiTest.java` |
| `PluginEngineTest` | Groovy/JS plugin engine basic capabilities | `src/test/java/com/xenoamess/damning_proxy/plugin/PluginEngineTest.java` |
| `PanacheLogRepositoryTest` | Log repository | `src/test/java/com/xenoamess/damning_proxy/repository/PanacheLogRepositoryTest.java` |
| `PanachePluginRepositoryTest` | Plugin repository | `src/test/java/com/xenoamess/damning_proxy/repository/PanachePluginRepositoryTest.java` |
| `PanacheProfileRepositoryTest` | Upstream profile repository | `src/test/java/com/xenoamess/damning_proxy/repository/PanacheProfileRepositoryTest.java` |
| `TrafficLogServiceTest` | Log service | `src/test/java/com/xenoamess/damning_proxy/service/TrafficLogServiceTest.java` |

---

## Manual Self-Testing

### 1. Start the Service

```bash
mvn quarkus:dev
```

### 2. Health Check

```bash
curl http://localhost:12360/v1/health
```

Expected response:

```json
{ "status": "ok", "database": "ok" }
```

### 3. Create Upstream Profile

```bash
curl -X POST http://localhost:12360/api/profiles \
  -H "Content-Type: application/json" \
  -d '{
    "name": "OpenAI",
    "slug": "openai",
    "baseUrl": "https://api.openai.com/v1",
    "bearerToken": "sk-xxxxxxxx",
    "timeoutMs": 30000,
    "enabled": true
  }'
```

### 4. Create Plugin Group

```bash
curl -X POST http://localhost:12360/api/plugin-groups \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Default Group",
    "slug": "default",
    "description": "default group",
    "enabled": true,
    "items": []
  }'
```

### 5. Create Instance

Assume profile id = 1, group id = 1:

```bash
curl -X POST http://localhost:12360/api/instances \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Instance",
    "slug": "my-instance",
    "profileId": 1,
    "pluginGroupId": 1,
    "enabled": true
  }'
```

### 6. Call the Proxy

```bash
curl -H "Authorization: Bearer sk-test" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": false
  }' \
  http://localhost:12360/v1/proxy/my-instance/chat/completions
```

### 7. View Logs

```bash
curl http://localhost:12360/api/logs
```

Expected PageResponse format:

```json
{
  "items": [
    {
      "id": 1,
      "instanceName": "My Instance",
      "requestPath": "/v1/proxy/my-instance/chat/completions",
      "requestMethod": "POST",
      "responseStatus": 200,
      "durationMs": 1234,
      "requestTime": "2026-06-21T10:00:00"
    }
  ],
  "total": 100,
  "limit": 20,
  "offset": 0
}
```

---

## Streaming Endpoint Self-Test

```bash
curl -N -H "Authorization: Bearer sk-test" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": true
  }' \
  http://localhost:12360/v1/proxy/my-instance/chat/completions
```

---

## Web Admin UI Self-Test

1. After starting the service, open `http://localhost:12360/admin/index.html`.
2. Create a Profile on the **Profile** page.
3. Write plugins on the **Plugin Management** page.
4. Compose plugins and adjust order on the **Plugin Group** page.
5. Create an Instance on the **Instance Management** page and bind a Profile and plugin group.
6. On the **Chat Test** page, select an Instance for streaming/non-streaming chat.
7. View the full request/response and plugin pipeline on the **Traffic Log** page.

---

## Test Upstream (Mock)

If you do not want to connect to real OpenAI, you can quickly start a mock with WireMock or Python:

```python
from flask import Flask, request, Response
import json

app = Flask(__name__)

@app.route('/v1/models', methods=['GET'])
def models():
    return {"object": "list", "data": [{"id": "mock-gpt-4o", "object": "model"}]}

@app.route('/v1/chat/completions', methods=['POST'])
def chat():
    body = request.json
    if body.get('stream'):
        def generate():
            data = json.dumps({"choices": [{"delta": {"content": "hello"}}]})
            yield f"data: {data}\n\n"
            yield "data: [DONE]\n\n"
        return Response(generate(), mimetype='text/event-stream')
    return {"id": "chatcmpl-1", "model": body.get("model"), "choices": [{"message": {"role": "assistant", "content": "Hello!"}}]}

if __name__ == '__main__':
    app.run(port=18089)
```

Set the Profile's `baseUrl` to `http://localhost:18089/v1` to test.

---

## Test Command Quick Reference

| Scenario | Command |
|---|---|
| Run all tests | `mvn test` |
| Run a single test class | `mvn test -Dtest=ProxyApiTest` |
| Native tests | `mvn clean verify -Pnative` |
| Health check | `curl http://localhost:12360/v1/health` |
| Create Profile | `curl -X POST http://localhost:12360/api/profiles ...` |
| Call proxy | `curl http://localhost:12360/v1/proxy/{slug}/chat/completions ...` |
| View logs | `curl http://localhost:12360/api/logs` |
