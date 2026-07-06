[中文版](01-proxy-api.md)

# 01 Proxy Endpoints

> Last updated: 2026-06-20  
> Source version: current workspace

Proxy endpoints are exposed based on the instance `slug`, with the path prefix `/v1/proxy/{instanceSlug}`.

Code entry point: `src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java`

---

## Endpoint List

| Method | Path | Content-Type | Description |
|---|---|---|---|
| `GET` | `/v1/proxy/{instanceSlug}/models` | `application/json` | Model list |
| `POST` | `/v1/proxy/{instanceSlug}/chat/completions` | `application/json` / `text/event-stream` | Unified chat completions endpoint; returns JSON or SSE based on the `stream` field |

---

## GET /v1/proxy/{instanceSlug}/models

Returns the upstream `/v1/models` response, processed by plugins.

### Request Example

```bash
curl http://localhost:12360/v1/proxy/my-instance/models
```

### Response Example

```json
{
  "object": "list",
  "data": [
    { "id": "gpt-4", "object": "model" }
  ]
}
```

### Status Codes

| Status Code | Description |
|---|---|
| 200 | Success |
| 403 | Instance or Profile is disabled |
| 404 | Instance does not exist |
| 502 | Upstream request failed |

---

## POST /v1/proxy/{instanceSlug}/chat/completions (Non-Streaming)

When the client request body contains `stream=false` (or omits `stream`), the full JSON response is returned.

### Request Example

```bash
curl -H "Authorization: Bearer sk-test" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": false
  }' \
  http://localhost:12360/v1/proxy/my-instance/chat/completions
```

### Response Example

```json
{
  "id": "chatcmpl-1",
  "object": "chat.completion",
  "model": "gpt-4",
  "choices": [
    { "message": { "role": "assistant", "content": "Hi" } }
  ]
}
```

### Notes

- Upstream authentication uses `ProxyProfile.bearerToken`; the `Authorization` header passed by the client is **not** used for upstream authentication.
- Header pass-through priority: client-provided > Profile custom headers > automatically injected `Authorization: Bearer {bearerToken}` > plugin modifications.
- Request body merge priority: client-provided > Profile `customBody` (passed to `ProxyProfile` via the `ProfileForm` DTO) > plugin modifications.

---

## POST /v1/proxy/{instanceSlug}/chat/completions (Streaming)

When the client request body contains `stream=true`, an SSE stream with `Content-Type: text/event-stream` is returned. This path is the **same endpoint** as the JSON one; the response format is switched based on the `stream` field. Implementation detail: the backend returns a `Response` that internally bridges `Multi<String>` to `jakarta.ws.rs.core.StreamingOutput`, explicitly setting `Content-Type: text/event-stream` so that Quarkus REST Reactive emits proper SSE instead of `Multi.toString()`.

Processing entry point: `src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java`

### Request Example

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

### Response Example

```text
data: {"choices":[{"delta":{"content":"Hello"}}]}

data: {"choices":[{"delta":{"content":"!"}}]}

data: [DONE]

```

### Streaming Implementation Details

- Each SSE event returned by the upstream is reformatted into the standard `data: ...\n\n`.
- If the upstream returns `data: [DONE]`, it is forwarded in the same format.
- Request-phase plugins execute before sending to the upstream.
- Response-phase plugins execute after the SSE stream is fully received, operating on accumulated content.
- If a request-phase plugin directly calls `returnResponse`, the response is wrapped into a single SSE event plus `data: [DONE]`.
- To avoid being disconnected by `readTimeout` during long upstream connections, the upstream HTTP client disables timeout when `Profile.timeoutMs <= 0`; meanwhile a heartbeat is logged to `TrafficLog.pluginLogs` every 30 seconds to diagnose long-waiting connections.

---

## Authentication Notes

- Proxy endpoints **do not validate** the client-provided `Authorization` header.
- It is only passed transparently to plugins or logs as optional information.
- Upstream authentication uses `ProxyProfile.bearerToken`.

---

## OpenAI Compatibility

The current implementation is compatible with the following OpenAI Chat Completions API capabilities:

- `model`
- `messages`
- `stream`
- Standard parameters such as temperature and top_p (passed through to the upstream)

Not compatible or not yet implemented:

- Special handling for tool calls (tools/function_calling)
- Local proxying of multimodal image URLs
- Per-chunk response plugin modifications in streaming mode (currently modified after the full stream is accumulated)
