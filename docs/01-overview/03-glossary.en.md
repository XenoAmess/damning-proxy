[中文版](03-glossary.md)

# 03 Glossary

> Last updated: 2026-06-17  
> Source version: current workspace

| Term | English | Description |
|---|---|---|
| 代理服务器 | Proxy Server | A service that exposes OpenAI-compatible endpoints and forwards requests to upstream sources. |
| 实例 | ProxyInstance | An externally exposed routing unit, accessed via `slug`, binding one upstream Profile and one plugin group. |
| 上游配置 / Profile | ProxyProfile | Configuration for an upstream OpenAI source, including baseUrl, bearerToken, custom headers, timeouts, etc. |
| 插件 | Plugin | A Groovy or JavaScript script that can modify request/response messages. |
| 插件组 | PluginGroup | A collection of plugins bound to an instance in order. |
| 插件组项 | PluginGroupItem | An entry in a plugin group, referencing a plugin and recording orderIndex, priority, and enabled status. |
| 执行阶段 | ExecutionPhase | Plugin execution phase: `REQUEST`, `RESPONSE`, or `BOTH`. |
| 流量日志 | TrafficLog | Records request/response messages, plugin logs, and execution snapshots for each proxy request. |
| 友好日志 | Friendly Log | Extracted user prompts, model outputs, plugin pipeline snapshots, etc., based on `TrafficLog`, for easy Web display. |
| 插件上下文 | PluginContext | The object plugin scripts can manipulate, including request/response body, headers, status code, logs, short-circuit control, etc. |
| 模型映射 | Model Mapping | A common plugin operation that rewrites the requested model name to the actual upstream model name. |
| SSE | Server-Sent Events | Streaming response protocol in `text/event-stream` format, used by OpenAI streaming endpoints. |
| slug | slug | A URL-friendly unique identifier used for accessing instances, plugin groups, and upstream profiles. |

---

## Entity Relationship Diagram

```text
ProxyProfile  1 ────── N  ProxyInstance
    │                         │
    │                         │
    │                         N  PluginGroup
    │                              │
    │                              N  PluginGroupItem
    │                                   │
    │                                   N  Plugin
    │
    └── Referenced by TrafficLog via instanceId/profileId
```

- One `ProxyProfile` can be used by multiple `ProxyInstance`s.
- One `ProxyInstance` uses one `PluginGroup`.
- One `PluginGroup` can contain multiple `PluginGroupItem`s; each item references one `Plugin`.
- `TrafficLog` associates with the corresponding entities via `instanceId` and `profileId`.

For full field descriptions, see [02-design/01-data-model.md](../02-design/01-data-model.en.md).
