[中文版](02-proxy-flow.md)

# 02 Proxy Request Processing Flow

> Last updated: 2026-07-08
> Corresponding source version: current workspace

## Common Preprocessing Steps

Regardless of `/models`, `/chat/completions`, `/embeddings`, `/images/generations`, `/audio/*`, or streaming `/chat/completions`, the following steps are executed first:

1. **Resolve Instance**: Find `ProxyInstance` by the `instanceSlug` in the URL.
2. **Validate Enabled Status**: Instance, Profile, and PluginGroup must all be enabled.
3. **Load Plugins**: Load enabled plugins from the PluginGroup in `sortedItems()` order.
4. **Record Request Log**: Create `TrafficLog` and write the request part.

Corresponding code: `src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java:289`, `src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java:314`, `src/main/java/com/xenoamess/damning_proxy/service/TrafficLogService.java:33`

---

## `/v1/proxy/{instanceSlug}/models`

Entry point: `src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java:22`

```text
OpenAiProxyService.listModels(instanceSlug)
  ├─ resolveInstance(instanceSlug)              Validate instance/profile/group
  ├─ loadPlugins(group)                         Load enabled plugins in order
  ├─ recordRequest(...)                         Record TrafficLog request part
  ├─ createRequestContext(profile, null)        Build PluginContext
  │     ├─ Set Authorization: Bearer <profile.bearerToken>
  │     └─ Parse profile.customHeaders JSON and add to request headers
  ├─ executeRequestPlugins(plugins, context)    Execute REQUEST / BOTH plugins
  │     └─ If context.isReturned() is true:
  │          ├─ recordResponse(...)             Record response
  │          └─ Return directly, skip upstream
  ├─ upstreamHttpClient.send("GET", baseUrl, "/models", ...)
  ├─ context.setResponseStatus(upstream.statusCode)
  ├─ context.setResponseBody(parseJson(upstream.body))
  ├─ executeResponsePlugins(plugins, context)   Execute RESPONSE / BOTH plugins
  ├─ recordResponse(...)                        Update TrafficLog response part
  └─ Return Response
```

---

## `/v1/proxy/{instanceSlug}/chat/completions` (Non-streaming)

Entry point: `src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java:30`

```text
OpenAiProxyService.chatCompletions(instanceSlug, requestBody)
  ├─ resolveInstance / loadPlugins / recordRequest
  ├─ createRequestContext(profile, requestBody)
  ├─ executeRequestPlugins(plugins, context)
  │     └─ If context.isReturned() is true:
  │          ├─ recordResponse(...)
  │          └─ Return directly
  ├─ upstreamHttpClient.send("POST", baseUrl, "/chat/completions", ...)
  ├─ Set response status/body
  ├─ executeResponsePlugins(plugins, context)
  ├─ recordResponse(...)
  └─ Return Response
```

`src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java:105`

---

## `/v1/proxy/{instanceSlug}/chat/completions` (Streaming)

Entry point: `src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java:40`

```text
OpenAiProxyService.chatCompletionsStream(instanceSlug, requestBody)
  ├─ resolveInstance / loadPlugins / recordRequest
  ├─ createRequestContext(profile, requestBody)
  ├─ executeRequestPlugins(plugins, context)
  │     └─ If context.isReturned() is true:
  │          ├─ recordResponse(...)
  │          └─ Wrap into a single SSE event and return:
  │               data: <returned JSON>
  │               data: [DONE]
  └─ streamChatCompletions(ctx, context, plugins, trafficLog, start)
```

### Streaming Delivery Logic

`src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java:176`

```text
upstreamHttpClient.sendStream(...) returns Future<HttpClientResponse>
  │
  ▼
Multi.createFrom().emitter(...)
  │
  ├─ response.handler(buffer)        Read upstream response chunk by chunk
  │     ├─ Split lines by \n
  │     ├─ Identify data: <payload>
  │     ├─ Filter [DONE]
  │     ├─ emitter.emit(payload)     Deliver to client immediately
  │     └─ accumulateStreamContent   Accumulate content / reasoning_content / tool_calls to contentBuffer
  │
  ├─ response.endHandler(v)          Stream ends
  │       ├─ buildStreamingResponseBody(contentBuffer.toString())
  │       ├─ context.setResponseBody(parsedBody)
  │       ├─ executeResponsePlugins(plugins, context)
  │       ├─ Async recordResponse(..., 200, ...)
  │       └─ emitter.complete()
  │
  └─ response.statusCode >= 400 or Future failure
        ├─ circuitBreaker.recordFailure(...)
        ├─ Async recordResponse(..., status, ...)
        ├─ emitter.emit("event: error\ndata: {...}\n\n")
        └─ emitter.complete()
```

### Streaming Plugin Execution Characteristics

- **Request-phase plugins**: Executed before sending to upstream; can rewrite model name, append system prompt, return directly, etc.
- **Response-phase plugins**: Executed after the complete SSE stream is received, operating on the **accumulated final response body**, not per chunk.
- If a request-phase plugin returns directly (`returnResponse`), the response is wrapped as a single SSE event plus `data: [DONE]` and returned.

---

## `/v1/proxy/{instanceSlug}/audio/*`

Entry point: `src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java:50` onwards

Three OpenAI audio endpoints are supported:

- `POST /v1/proxy/{instanceSlug}/audio/transcriptions`
- `POST /v1/proxy/{instanceSlug}/audio/translations`
- `POST /v1/proxy/{instanceSlug}/audio/speech`

Compared with ordinary OpenAI proxy endpoints, audio request/response bodies may be `multipart/form-data` or binary, so `ProxyApi` passes the body bytes through directly without JSON parsing:

```text
OpenAiProxyService.audioTranscriptions / audioTranslations / audioSpeech
  ├─ resolveInstance / loadPlugins / recordRequest
  ├─ createRequestContext(profile, requestBody)
  ├─ executeRequestPlugins(plugins, context)
  │     └─ If context.isReturned() is true: recordResponse and return directly
  ├─ upstreamHttpClient.sendBinary(...)  Pass through request body and Content-Type
  ├─ Set response status/body
  ├─ executeResponsePlugins(plugins, context)
  ├─ recordResponse(...)
  └─ Return Response
```

Audio response content is returned to the client as a byte array; plugins can read/modify the byte array in `context.responseBody`.

---

## PluginContext Construction

`src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java:327`

```text
createRequestContext(profile, requestBody)
  ├─ context.setRequestBody(requestBody)
  ├─ If profile.bearerToken is not empty:
  │     context.requestHeaders.put("Authorization", "Bearer " + profile.bearerToken)
  └─ addCustomHeaders(context, profile)
        ├─ Parse profile.customHeaders JSON
        └─ Add each key-value to context.requestHeaders
```

These request headers are then converted to Vert.x `MultiMap` via `toMultiMap()` and sent to upstream by `UpstreamHttpClient`.

---

## Upstream Request Construction

`src/main/java/com/xenoamess/damning_proxy/proxy/UpstreamHttpClient.java:35`

```text
send(method, baseUrl, path, headers, body, timeoutMs)
  ├─ buildUri(baseUrl, path)        Merge into full URI
  ├─ Set host / port / SSL / URI
  ├─ Set timeout (default 30s)
  ├─ Copy headers, skip Host header
  ├─ If body is not empty, serialize to JSON and set Content-Type: application/json
  └─ Synchronously wait for response, return UpstreamResponse
```

- The synchronous method blocks via `future.toCompletionStage().toCompletableFuture().get()`.
- The `Host` header is automatically stripped to avoid upstream rejecting requests due to Host mismatch.

---

## Exception Handling

`src/main/java/com/xenoamess/damning_proxy/filter/GlobalExceptionMapper.java:13`

| Exception Type | Behavior |
|---|---|
| `WebApplicationException` with status 400 | Wrapped as OpenAI-style `{ error: { message, type: "invalid_request_error" } }` |
| Other `WebApplicationException` | Passed through as-is |
| Non-WebApplicationException | Returns 500, OpenAI-style `{ error: { message, type: "internal_error" } }` |

---

## Status Code Quick Reference

| Status Code | Trigger Scenario |
|---|---|
| 200 | Proxy success |
| 400 | Request parameter error (admin API) |
| 403 | Instance or Profile is disabled |
| 404 | Instance / Profile / PluginGroup does not exist |
| 409 | slug conflict (admin API) |
| 502 | Upstream request failed |
| 500 | Uncaught exception |
