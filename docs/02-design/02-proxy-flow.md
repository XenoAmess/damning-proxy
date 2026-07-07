[English Version](02-proxy-flow.en.md)

# 02 代理请求处理流程

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区

## 公共前置步骤

无论 `/models`、`/chat/completions`、`/embeddings`、`/images/generations` 还是流式 `/chat/completions`，都会先执行：

1. **解析 Instance**：根据 URL 中的 `instanceSlug` 查找 `ProxyInstance`。
2. **校验启用状态**：Instance、Profile、PluginGroup 必须均启用。
3. **加载插件**：从 PluginGroup 中按 `sortedItems()` 顺序加载启用的插件。
4. **记录请求日志**：创建 `TrafficLog`，写入请求部分。

对应代码：`src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java:289`、`src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java:314`、`src/main/java/com/xenoamess/damning_proxy/service/TrafficLogService.java:33`

---

## `/v1/proxy/{instanceSlug}/models`

处理入口：`src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java:22`

```text
OpenAiProxyService.listModels(instanceSlug)
  ├─ resolveInstance(instanceSlug)              校验 instance/profile/group
  ├─ loadPlugins(group)                         按顺序加载启用插件
  ├─ recordRequest(...)                         记录 TrafficLog 请求部分
  ├─ createRequestContext(profile, null)        构造 PluginContext
  │     ├─ 设置 Authorization: Bearer <profile.bearerToken>
  │     └─ 解析 profile.customHeaders JSON 并加入请求头
  ├─ executeRequestPlugins(plugins, context)    执行 REQUEST / BOTH 插件
  │     └─ 若 context.isReturned() 为 true：
  │          ├─ recordResponse(...)             记录响应
  │          └─ 直接返回，跳过上游
  ├─ upstreamHttpClient.send("GET", baseUrl, "/models", ...)
  ├─ context.setResponseStatus(upstream.statusCode)
  ├─ context.setResponseBody(parseJson(upstream.body))
  ├─ executeResponsePlugins(plugins, context)   执行 RESPONSE / BOTH 插件
  ├─ recordResponse(...)                        更新 TrafficLog 响应部分
  └─ 返回 Response
```

---

## `/v1/proxy/{instanceSlug}/chat/completions`（非流式）

处理入口：`src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java:30`

```text
OpenAiProxyService.chatCompletions(instanceSlug, requestBody)
  ├─ resolveInstance / loadPlugins / recordRequest
  ├─ createRequestContext(profile, requestBody)
  ├─ executeRequestPlugins(plugins, context)
  │     └─ 若 context.isReturned() 为 true：
  │          ├─ recordResponse(...)
  │          └─ 直接返回
  ├─ upstreamHttpClient.send("POST", baseUrl, "/chat/completions", ...)
  ├─ 设置响应状态/响应体
  ├─ executeResponsePlugins(plugins, context)
  ├─ recordResponse(...)
  └─ 返回 Response
```

`src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java:105`

---

## `/v1/proxy/{instanceSlug}/chat/completions`（流式）

处理入口：`src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java:40`

```text
OpenAiProxyService.chatCompletionsStream(instanceSlug, requestBody)
  ├─ resolveInstance / loadPlugins / recordRequest
  ├─ createRequestContext(profile, requestBody)
  ├─ executeRequestPlugins(plugins, context)
  │     └─ 若 context.isReturned() 为 true：
  │          ├─ recordResponse(...)
  │          └─ 包装成单个 SSE 事件返回：
  │               data: <returned JSON>
  │               data: [DONE]
  └─ streamChatCompletions(ctx, context, plugins, trafficLog, start)
```

### 流式下发逻辑

`src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java:176`

```text
upstreamHttpClient.sendStream(...) 返回 Future<HttpClientResponse>
  │
  ▼
Multi.createFrom().emitter(...)
  │
  ├─ response.handler(buffer)        逐 chunk 读取上行响应
  │     ├─ 按 \n 切分行
  │     ├─ 识别 data: <payload>
  │     ├─ 过滤 [DONE]
  │     ├─ emitter.emit(payload)     立即下发给客户端
  │     └─ accumulateStreamContent   累积 content / reasoning_content / tool_calls 到 contentBuffer
  │
  ├─ response.endHandler(v)          流结束
  │       ├─ buildStreamingResponseBody(contentBuffer.toString())
  │       ├─ context.setResponseBody(parsedBody)
  │       ├─ executeResponsePlugins(plugins, context)
  │       ├─ 异步 recordResponse(..., 200, ...)
  │       └─ emitter.complete()
  │
  └─ response.statusCode >= 400 或 Future 失败
        ├─ circuitBreaker.recordFailure(...)
        ├─ 异步 recordResponse(..., status, ...)
        ├─ emitter.emit("event: error\ndata: {...}\n\n")
        └─ emitter.complete()
```

### 流式插件执行特点

- **请求阶段插件**：在发送上游前执行，可以改写模型名、追加 system prompt、直接返回等。
- **响应阶段插件**：在完整 SSE 流接收完毕后执行，操作的是**累积后的最终响应体**，不是逐 chunk。
- 若请求阶段插件直接返回（`returnResponse`），响应会包装成单个 SSE 事件加 `data: [DONE]` 返回。

---

## PluginContext 的构造

`src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java:327`

```textncreateRequestContext(profile, requestBody)
  ├─ context.setRequestBody(requestBody)
  ├─ 若 profile.bearerToken 非空：
  │     context.requestHeaders.put("Authorization", "Bearer " + profile.bearerToken)
  └─ addCustomHeaders(context, profile)
        ├─ 解析 profile.customHeaders JSON
        └─ 逐 key-value 加入 context.requestHeaders
```

这些请求头随后通过 `toMultiMap()` 转成 Vert.x `MultiMap`，由 `UpstreamHttpClient` 发送到上游。

---

## 上游请求构造

`src/main/java/com/xenoamess/damning_proxy/proxy/UpstreamHttpClient.java:35`

```text
send(method, baseUrl, path, headers, body, timeoutMs)
  ├─ buildUri(baseUrl, path)        合并为完整 URI
  ├─ 设置 host / port / SSL / URI
  ├─ 设置超时（默认 30s）
  ├─ 复制 headers，跳过 Host 头
  ├─ 若 body 非空，序列化为 JSON 并设置 Content-Type: application/json
  └─ 同步等待响应，返回 UpstreamResponse
```

- 同步方法通过 `future.toCompletionStage().toCompletableFuture().get()` 阻塞等待。
- `Host` 头会被自动剥离，避免上游因 Host 不匹配拒绝请求。

---

## 异常处理

`src/main/java/com/xenoamess/damning_proxy/filter/GlobalExceptionMapper.java:13`

| 异常类型 | 行为 |
|---|---|
| `WebApplicationException` 且状态码 400 | 包装为 OpenAI 风格 `{ error: { message, type: "invalid_request_error" } }` |
| 其他 `WebApplicationException` | 直接透传原始响应 |
| 非 WebApplicationException | 返回 500，OpenAI 风格 `{ error: { message, type: "internal_error" } }` |

---

## 状态码速查

| 状态码 | 触发场景 |
|---|---|
| 200 | 代理成功 |
| 400 | 请求参数错误（管理 API） |
| 403 | Instance 或 Profile 被禁用 |
| 404 | Instance / Profile / PluginGroup 不存在 |
| 409 | slug 冲突（管理 API） |
| 502 | 上游请求失败 |
| 500 | 未捕获异常 |
