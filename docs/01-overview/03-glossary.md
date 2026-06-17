# 03 术语表

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区

| 术语 | 英文 | 说明 |
|---|---|---|
| 代理服务器 | Proxy Server | 对外暴露 OpenAI 兼容接口，将请求转发到上游源的服务。 |
| 实例 | ProxyInstance | 对外暴露的路由单元，通过 `slug` 访问，绑定一个上游 Profile 和一个插件组。 |
| 上游配置 / Profile | ProxyProfile | 上游 OpenAI 源的配置，包括 baseUrl、bearerToken、自定义 Header、超时等。 |
| 插件 | Plugin | 一段 Groovy 或 JavaScript 脚本，可在请求/响应阶段篡改报文。 |
| 插件组 | PluginGroup | 一组插件的集合，按顺序绑定到实例。 |
| 插件组项 | PluginGroupItem | 插件组中的条目，关联一个插件，并记录 orderIndex、priority、enabled。 |
| 执行阶段 | ExecutionPhase | 插件执行阶段：`REQUEST`（请求）、`RESPONSE`（响应）、`BOTH`（两者）。 |
| 流量日志 | TrafficLog | 记录每次代理请求的请求/响应报文、插件日志与执行快照。 |
| 友好日志 | Friendly Log | 在 `TrafficLog` 基础上提取的用户提示词、模型输出、插件流水线快照等，便于 Web 展示。 |
| 插件上下文 | PluginContext | 插件脚本可操作的对象，包含请求/响应体、头、状态码、日志、短路控制等。 |
| 模型映射 | Model Mapping | 插件中常见的把请求模型名改写为上游实际模型名的操作。 |
| SSE | Server-Sent Events | 流式响应协议，`text/event-stream` 格式，OpenAI 流式接口使用。 |
| slug | slug | URL 友好的唯一标识符，用于实例、插件组、上游配置的访问。 |

---

## 实体关系图

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
    └── 通过 instanceId/profileId 被 TrafficLog 引用
```

- 一个 `ProxyProfile` 可被多个 `ProxyInstance` 使用。
- 一个 `ProxyInstance` 使用一个 `PluginGroup`。
- 一个 `PluginGroup` 可包含多个 `PluginGroupItem`，每个 item 引用一个 `Plugin`。
- `TrafficLog` 通过 `instanceId` 与 `profileId` 关联对应实体。

完整字段说明见 [02-design/01-data-model.md](../02-design/01-data-model.md)。
