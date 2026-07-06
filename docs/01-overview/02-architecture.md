[English Version](02-architecture.en.md)

# 02 整体架构

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区

## 架构分层

```text
┌─────────────────────────────────────────────────────────────────────┐
│  前端层 (admin-web)                                                  │
│  Vue3 + VueRouter + ElementPlus + Axios + CodeMirror + marked        │
│  构建产物输出到：src/main/resources/META-INF/resources/admin          │
├─────────────────────────────────────────────────────────────────────┤
│  REST / 资源层                                                       │
│  api/         : HomeResource, HealthResource, admin/*AdminApi        │
│  proxy/       : ProxyApi（OpenAI 协议代理入口）                       │
├─────────────────────────────────────────────────────────────────────┤
│  业务服务层                                                          │
│  proxy/       : OpenAiProxyService（代理编排）                        │
│  proxy/       : UpstreamHttpClient（Vert.x 上行请求）                 │
│  proxy/       : CircuitBreaker（上游熔断器）                          │
│  proxy/       : RateLimiter（请求速率限制器）                         │
│  service/     : TrafficLogService（流量日志记录）                     │
│  plugin/      : PluginExecutionService（插件流水线执行）              │
├─────────────────────────────────────────────────────────────────────┤
│  数据访问层                                                          │
│  repository/        : 仓库接口（Instance/Profile/Plugin/Group/Log）  │
│  repository/panache/ : Panache 实现                                  │
├─────────────────────────────────────────────────────────────────────┤
│  数据模型层                                                          │
│  entity/      : ProxyInstance, ProxyProfile, Plugin, PluginGroup,    │
│                 PluginGroupItem, TrafficLog                          │
├─────────────────────────────────────────────────────────────────────┤
│  基础设施                                                            │
│  filter/      : GlobalExceptionMapper                                │
│  migration/   : StartupMigration（示例数据与自动迁移）                │
│  resources/   : application.properties                               │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 核心请求路径

### 非流式聊天补全

```text
Client
  │ POST /v1/proxy/{instanceSlug}/chat/completions
  │ Accept: application/json
  ▼
ProxyApi.chatCompletions()                         [src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java:30]
  │
  ▼
OpenAiProxyService.chatCompletions()               [src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java:105]
  ├─ resolveInstance(instanceSlug)                 [OpenAiProxyService.java:289]
  ├─ loadPlugins(group)                            [OpenAiProxyService.java:314]
  ├─ recordRequest(...)                            [TrafficLogService.java:33]
  ├─ createRequestContext(profile, body)           [OpenAiProxyService.java:327]
  ├─ pluginExecutionService.executeRequestPlugins  [PluginExecutionService.java:16]
  ├─ upstreamHttpClient.send(...)                  [UpstreamHttpClient.java:35]
  ├─ pluginExecutionService.executeResponsePlugins [PluginExecutionService.java:20]
  ├─ recordResponse(...)                           [TrafficLogService.java:48]
  └─ 返回 Response
```

### 流式聊天补全

```text
Client
  │ POST /v1/proxy/{instanceSlug}/chat/completions
  │ Accept: text/event-stream
  ▼
ProxyApi.chatCompletionsStream()                   [ProxyApi.java:40]
  │
  ▼
OpenAiProxyService.chatCompletionsStream()         [OpenAiProxyService.java:152]
  ├─ 请求阶段插件执行
  ├─ upstreamHttpClient.sendStream(...)            [UpstreamHttpClient.java:85]
  ├─ Multi<String> SSE 逐 chunk 下发
  └─ 流结束后执行响应阶段插件并记录日志
```

详细流程见 [02 代理请求处理流程](../02-design/02-proxy-flow.md)。

---

## 组件职责

| 包/类 | 职责 | 关键文件 |
|---|---|---|
| `api/` | REST 端点定义 | `src/main/java/com/xenoamess/damning_proxy/api/HomeResource.java`, `src/main/java/com/xenoamess/damning_proxy/api/HealthResource.java`, `src/main/java/com/xenoamess/damning_proxy/api/admin/` |
| `proxy/` | OpenAI 代理核心与上行 HTTP | `src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java`, `src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java`, `src/main/java/com/xenoamess/damning_proxy/proxy/UpstreamHttpClient.java`, `src/main/java/com/xenoamess/damning_proxy/proxy/CircuitBreaker.java`, `src/main/java/com/xenoamess/damning_proxy/proxy/RateLimiter.java` |
| `proxy/CircuitBreaker` | 上游故障熔断保护 | `src/main/java/com/xenoamess/damning_proxy/proxy/CircuitBreaker.java` |
| `proxy/RateLimiter` | 代理请求速率限制 | `src/main/java/com/xenoamess/damning_proxy/proxy/RateLimiter.java` |
| `plugin/` | 插件上下文、执行器、引擎 | `src/main/java/com/xenoamess/damning_proxy/plugin/PluginContext.java`, `src/main/java/com/xenoamess/damning_proxy/plugin/PluginExecutionService.java`, `src/main/java/com/xenoamess/damning_proxy/plugin/engine/` |
| `entity/` | JPA 实体 | `src/main/java/com/xenoamess/damning_proxy/entity/` |
| `repository/` | 数据访问接口与 Panache 实现 | `src/main/java/com/xenoamess/damning_proxy/repository/`, `src/main/java/com/xenoamess/damning_proxy/repository/panache/` |
| `service/` | 业务服务 | `src/main/java/com/xenoamess/damning_proxy/service/TrafficLogService.java` |
| `filter/` | 全局异常处理 | `src/main/java/com/xenoamess/damning_proxy/filter/GlobalExceptionMapper.java` |
| `migration/` | 启动迁移与示例数据 | `src/main/java/com/xenoamess/damning_proxy/migration/StartupMigration.java` |

---

## 前端架构

```text
admin-web/src/
├── main.js           # Vue 应用入口
├── App.vue           # 侧边栏布局
├── router.js         # 路由配置
├── style.css         # 全局样式
├── api/              # API 封装
│   ├── damning.js    # 管理后台 API
│   └── chat.js       # 代理端点 API（含 SSE）
├── components/       # 可复用组件
│   └── CodeEditor.vue
└── views/            # 页面
    ├── Instances.vue
    ├── Profiles.vue
    ├── Plugins.vue
    ├── PluginGroups.vue
    ├── Chat.vue
    └── Logs.vue
```

前端构建产物输出到 `src/main/resources/META-INF/resources/admin/`，由 Quarkus 作为静态资源托管。

---

## 设计要点

- **无认证**：所有 `/api/*` 与 `/v1/proxy/*` 均公开访问，仅依赖 `profile.bearerToken` 作为上行认证。如需认证，应在前置反向代理或后续版本中扩展。
- **实例隔离**：每个 `ProxyInstance` 独立绑定一个上游 Profile 和一组插件，外部通过 `instanceSlug` 访问。
- **H2 默认**：使用文件型 H2 便于本地开发，生产环境可切换为 PostgreSQL/MySQL。
- **上行强制 HTTP/1.1**：`UpstreamHttpClient` 关闭 ALPN，强制使用 HTTP/1.1。Vert.x `HttpClient` 在 HTTP/2 下从工作线程读取 `response.body()` 时会出现响应体丢失/截断的问题，导致非流式返回变成非 JSON（如流量日志 #71）。
- **插件拥有完整 JVM 权限**：Groovy 与 GraalJS 均可执行任意代码，部署时需谨慎。

安全提示详见 [04 安全提示](../02-design/04-security-notes.md)。
