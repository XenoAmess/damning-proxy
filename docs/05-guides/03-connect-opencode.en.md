[中文版](03-connect-opencode.md)

# 03 Connect OpenCode

> Last updated: 2026-06-17  
> Source version: current workspace

## Configure opencode.json

Add damning-proxy as a custom provider in `opencode.json`:

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

- `baseURL` points to your instance slug, e.g. `my-instance`.
- `apiKey` can be any value; the proxy endpoint does not validate it.
- The keys in `models` must match the model IDs returned by the upstream `/v1/models`.

---

## Confirm Upstream Models

damning-proxy's `/v1/proxy/{instanceSlug}/models` passes through the upstream model list.

Make sure the model IDs configured in `models` can be recognized by the upstream; otherwise OpenCode may report an error when calling.

---

## CORS

Because the OpenCode client may access damning-proxy from the browser, CORS is enabled by default in `src/main/resources/application.properties`:

```properties
quarkus.http.cors=true
quarkus.http.cors.origins=*
```

In production, restrict this to trusted origins.

---

## Troubleshooting

- Confirm damning-proxy is running and listening on port `12360`.
- Confirm the instance slug is correct.
- Confirm the upstream Profile's `baseUrl` and `bearerToken` are correct.
- Check damning-proxy's traffic logs to confirm whether requests are arriving.
