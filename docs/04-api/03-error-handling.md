# 03 错误处理

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区

## 全局异常处理

`src/main/java/com/xenoamess/damning_proxy/filter/GlobalExceptionMapper.java:13`

所有未捕获异常统一由 `GlobalExceptionMapper` 处理。

---

## 错误响应格式

### 400 Bad Request

OpenAI 兼容风格：

```json
{
  "error": {
    "message": "Streaming requests are not supported by this endpoint, use /chat/completions with Accept: text/event-stream",
    "type": "invalid_request_error"
  }
}
```

触发场景：

- 请求体中 `stream=true` 却调用非流式端点。
- 管理 API 参数缺失（此时可能返回纯文本，如 `slug is required`）。

---

### 500 Internal Server Error

```json
{
  "error": {
    "message": "Internal server error: ...",
    "type": "internal_error"
  }
}
```

触发场景：

- 未捕获的运行时异常。
- 上游请求严重失败等。

---

## 代理端点状态码

| 状态码 | 触发场景 |
|---|---|
| 200 | 代理成功 |
| 400 | 请求参数错误，或 stream=true 却调用非流式端点 |
| 403 | Instance 或 Profile 被禁用 |
| 404 | Instance / Profile / PluginGroup 不存在 |
| 429 | 请求频率超限（rate limit exceeded） |
| 502 | 上游请求失败（`UpstreamHttpClient` 抛出的 `WebApplicationException(BAD_GATEWAY)`） |
| 500 | 未捕获异常 |
| 503 | 上游断路器打开（circuit breaker open），服务暂时不可用 |

---

## 管理 API 状态码

| 状态码 | 触发场景 |
|---|---|
| 200 | GET/PUT 成功 |
| 201 | POST 创建成功 |
| 204 | DELETE 成功 |
| 400 | 参数错误（缺少 slug、profileId 不存在等） |
| 404 | 资源不存在 |
| 409 | slug 冲突 |

---

## 日志中的错误

`GlobalExceptionMapper` 会打印所有未捕获异常到日志：

```text
ERROR [com.xenoamess.damning_proxy.filter.GlobalExceptionMapper] Unhandled exception
```

遇到 500 时，优先查看服务端日志定位原因。
