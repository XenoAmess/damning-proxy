# 03 接入 OpenCode

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区

## 配置 opencode.json

在 `opencode.json` 中添加 damning-proxy 作为自定义 provider：

```json
{
  "$schema": "https://opencode.ai/config.json",
  "provider": {
    "damning-proxy": {
      "npm": "@ai-sdk/openai-compatible",
      "name": "damning-proxy (local)",
      "options": {
        "baseURL": "http://localhost:12360/v1/proxy/my-instance",
        "apiKey": "sk-damning-proxy-demo-token"
      },
      "models": {
        "mock-gpt-4o": {
          "name": "Mock GPT-4o"
        },
        "mock-claude-3-sonnet": {
          "name": "Mock Claude 3 Sonnet"
        }
      }
    }
  }
}
```

- `baseURL` 指向你的 instance slug，例如 `my-instance`。
- `apiKey` 可任意填写，代理端点不验证它。
- `models` 中的 key 需要与上游 `/v1/models` 返回的模型 ID 一致。

---

## 确认上游模型

 damning-proxy 的 `/v1/proxy/{instanceSlug}/models` 会透传上游模型列表。

确保 `models` 中配置的模型 ID 能被上游识别，否则 OpenCode 调用时可能报错。

---

## CORS

由于 OpenCode 客户端可能从浏览器访问 damning-proxy，`src/main/resources/application.properties` 默认开启 CORS：

```properties
quarkus.http.cors=true
quarkus.http.cors.origins=*
```

生产环境应限制为可信来源。

---

## 故障排查

- 确认 damning-proxy 已启动并监听 `12360` 端口。
- 确认 instance slug 正确。
- 确认上游 Profile 的 `baseUrl` 和 `bearerToken` 正确。
- 查看 damning-proxy 的流量日志确认请求是否到达。
