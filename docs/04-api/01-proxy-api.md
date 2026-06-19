# 01 代理端点

> 最后更新：2026-06-20  
> 对应源码版本：当前工作区

代理端点基于实例的 `slug` 对外暴露，路径前缀为 `/v1/proxy/{instanceSlug}`。

代码入口：`src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java`

---

## 端点清单

| Method | Path | Content-Type | 说明 |
|---|---|---|---|
| `GET` | `/v1/proxy/{instanceSlug}/models` | `application/json` | 模型列表 |
| `POST` | `/v1/proxy/{instanceSlug}/chat/completions` | `application/json` / `text/event-stream` | 统一聊天补全端点，根据 `stream` 字段返回 JSON 或 SSE |

---

## GET /v1/proxy/{instanceSlug}/models

返回上游 `/v1/models` 的响应，经过插件处理。

### 请求示例

```bash
curl http://localhost:12360/v1/proxy/my-instance/models
```

### 响应示例

```json
{
  "object": "list",
  "data": [
    { "id": "gpt-4", "object": "model" }
  ]
}
```

### 状态码

| 状态码 | 说明 |
|---|---|
| 200 | 成功 |
| 403 | Instance 或 Profile 被禁用 |
| 404 | Instance 不存在 |
| 502 | 上游请求失败 |

---

## POST /v1/proxy/{instanceSlug}/chat/completions（非流式）

客户端请求体中 `stream=false`（或省略）时返回完整 JSON。

### 请求示例

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

### 响应示例

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

### 注意

- 上行认证使用 `ProxyProfile.bearerToken`，客户端传入的 `Authorization` 头**不会**被用于上游认证。
- 请求头透传优先级：客户端传入 > Profile 自定义头 > 自动注入的 `Authorization: Bearer {bearerToken}` > 插件修改。
- 请求体合并优先级：客户端传入 > Profile `customBody` > 插件修改。

---

## POST /v1/proxy/{instanceSlug}/chat/completions（流式）

客户端请求体中 `stream=true` 时返回 `text/event-stream` SSE 流。该路径与 JSON 端点是**同一个 endpoint**，通过判断 `stream` 字段来切换响应格式。实现细节：后端返回 `Response`，内部使用 `jakarta.ws.rs.core.StreamingOutput` 桥接 `Multi<String>`，显式设置 `Content-Type: text/event-stream`，确保 Quarkus REST Reactive 正确输出 SSE 而不是 `Multi.toString()`。

处理入口：`src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java`

### 请求示例

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

### 响应示例

```text
data: {"choices":[{"delta":{"content":"Hello"}}]}

data: {"choices":[{"delta":{"content":"!"}}]}

data: [DONE]

```

### 流式实现细节

- 上游返回的每个 SSE 事件会被重新格式化为标准 `data: ...\n\n`。
- 若上游返回 `data: [DONE]`，也会按相同格式转发。
- 请求阶段插件在发送上游前执行。
- 响应阶段插件在 SSE 完整接收后执行，操作累积内容。
- 若请求阶段插件直接 `returnResponse`，响应被包装成单个 SSE 事件加 `data: [DONE]`。
- 为避免上游长连接期间被 `readTimeout` 断开，当 Profile `timeoutMs <= 0` 时上游 HTTP 客户端会禁用超时；同时每 30 秒记录一次心跳到 `TrafficLog.pluginLogs`，用于诊断长时间等待的连接。

---

## 认证说明

- 代理端点**不验证**客户端传入的 `Authorization`。
- 它仅作为可选信息透传给插件或日志。
- 上行认证使用 `ProxyProfile.bearerToken`。

---

## OpenAI 兼容性

当前实现兼容 OpenAI Chat Completions API 的以下能力：

- `model`
- `messages`
- `stream`
- 温度、top_p 等标准参数（透传到上游）

不兼容或尚未实现：

- 工具调用（tools/function_calling）相关的特殊处理
- 多模态图片 URL 的本地代理
- 流式下的逐 chunk 响应插件修改（当前是整体累积后修改）
