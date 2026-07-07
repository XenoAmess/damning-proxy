[中文版](01-proxy-api.md)

# 01 Proxy Endpoints

> Last updated: 2026-07-07  
> Source version: current workspace

Proxy endpoints are exposed based on the instance `slug`, with the path prefix `/v1/proxy/{instanceSlug}`.

Code entry point: `src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java`

---

## Endpoint List

| Method | Path | Content-Type | Description |
|---|---|---|---|
| `GET` | `/v1/proxy/{instanceSlug}/models` | `application/json` | Model list |
| `POST` | `/v1/proxy/{instanceSlug}/chat/completions` | `application/json` / `text/event-stream` | Unified chat completions endpoint; returns JSON or SSE based on the `stream` field |
| `POST` | `/v1/proxy/{instanceSlug}/embeddings` | `application/json` | Text embeddings |
| `POST` | `/v1/proxy/{instanceSlug}/images/generations` | `application/json` | Image generations |

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

### Streaming Error Handling

When the upstream connection fails, returns a non-2xx status code, or a stream read error occurs, the proxy does not close the connection abruptly. Instead, it sends an SSE `event: error` event to the client and then completes the stream normally. The event format is:

```text
event: error
data: {"error":{"message":"Upstream returned 500: ...","code":500}}

```

Clients should listen for and parse this event so the error message is surfaced to the user.

### Streaming Implementation Details

- Each SSE event returned by the upstream is reformatted into the standard `data: ...\n\n`.
- If the upstream returns `data: [DONE]`, it is forwarded in the same format.
- Request-phase plugins execute before sending to the upstream.
- Response-phase plugins execute after the SSE stream is fully received, operating on accumulated content.
- If a request-phase plugin directly calls `returnResponse`, the response is wrapped into a single SSE event plus `data: [DONE]`.
- To avoid being disconnected by `readTimeout` during long upstream connections, the upstream HTTP client disables timeout when `Profile.timeoutMs <= 0`; meanwhile a heartbeat is logged to `TrafficLog.pluginLogs` every 30 seconds to diagnose long-waiting connections.

---

## POST /v1/proxy/{instanceSlug}/embeddings

Compatible with the OpenAI Embeddings API. The request body is passed through to the upstream `/v1/embeddings` endpoint, and the response is returned after plugin processing.

### Request Example

```bash
curl -H "Authorization: Bearer sk-test" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "text-embedding-3-small",
    "input": "hello"
  }' \
  http://localhost:12360/v1/proxy/my-instance/embeddings
```

### Response Example

```json
{
  "object": "list",
  "data": [
    { "object": "embedding", "embedding": [0.1, 0.2], "index": 0 }
  ],
  "model": "text-embedding-3-small",
  "usage": { "prompt_tokens": 2, "total_tokens": 2 }
}
```

---

## POST /v1/proxy/{instanceSlug}/images/generations

Compatible with the OpenAI Images Generations API. The request body is passed through to the upstream `/v1/images/generations` endpoint, and the response is returned after plugin processing.

### Request Example

```bash
curl -H "Authorization: Bearer sk-test" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "dall-e-3",
    "prompt": "a cat",
    "n": 1,
    "size": "1024x1024"
  }' \
  http://localhost:12360/v1/proxy/my-instance/images/generations
```

### Response Example

```json
{
  "created": 1234567890,
  "data": [
    { "url": "http://example.com/image.png" }
  ]
}
```

---

## Authentication Notes

- Proxy endpoints **do not validate** the client-provided `Authorization` header.
- It is only passed transparently to plugins or logs as optional information.
- Upstream authentication uses `ProxyProfile.bearerToken`.

---

## OpenAI Compatibility

The current implementation is compatible with the following OpenAI API capabilities:

- Chat Completions: `model`, `messages`, `stream`, and standard parameters such as temperature and top_p.
- Embeddings: `model`, `input`, and other standard parameters.
- Images Generations: `model`, `prompt`, `n`, `size`, and other standard parameters.
- Tool calls (`tool_calls`) are recognized and forwarded in both streaming and non-streaming responses.

Not compatible or not yet implemented:

- Local proxying of multimodal image URLs
- Per-chunk response plugin modifications in streaming mode (currently modified after the full stream is accumulated)
