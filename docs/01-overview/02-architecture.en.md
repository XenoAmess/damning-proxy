[中文版](02-architecture.md)

# 02 Architecture Overview

> Last updated: 2026-06-17  
> Source version: current workspace

## Architectural Layers

```text
┌─────────────────────────────────────────────────────────────────────┐
│  Frontend Layer (admin-web)                                         │
│  Vue3 + VueRouter + ElementPlus + Axios + CodeMirror + marked       │
│  Build output: src/main/resources/META-INF/resources/admin          │
├─────────────────────────────────────────────────────────────────────┤
│  REST / Resource Layer                                              │
│  api/         : HomeResource, HealthResource, admin/*AdminApi       │
│  proxy/       : ProxyApi (OpenAI protocol proxy entry)              │
├─────────────────────────────────────────────────────────────────────┤
│  Business Service Layer                                             │
│  proxy/       : OpenAiProxyService (proxy orchestration)            │
│  proxy/       : UpstreamHttpClient (Vert.x upstream request)        │
│  proxy/       : CircuitBreaker (upstream circuit breaker)           │
│  proxy/       : RateLimiter (request rate limiter)                  │
│  service/     : TrafficLogService (traffic log recording)           │
│  plugin/      : PluginExecutionService (plugin pipeline execution)  │
├─────────────────────────────────────────────────────────────────────┤
│  Data Access Layer                                                  │
│  repository/        : Repository interfaces (Instance/Profile/Plugin/Group/Log) │
│  repository/panache/ : Panache implementations                      │
├─────────────────────────────────────────────────────────────────────┤
│  Data Model Layer                                                   │
│  entity/      : ProxyInstance, ProxyProfile, Plugin, PluginGroup,   │
│                 PluginGroupItem, TrafficLog                         │
├─────────────────────────────────────────────────────────────────────┤
│  Infrastructure                                                     │
│  filter/      : GlobalExceptionMapper                               │
│  migration/   : StartupMigration (sample data and auto-migration)   │
│  resources/   : application.properties                              │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Core Request Paths

### Non-streaming Chat Completion

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
  └─ Return Response
```

### Streaming Chat Completion

```text
Client
  │ POST /v1/proxy/{instanceSlug}/chat/completions
  │ Accept: text/event-stream
  ▼
ProxyApi.chatCompletionsStream()                   [ProxyApi.java:40]
  │
  ▼
OpenAiProxyService.chatCompletionsStream()         [OpenAiProxyService.java:152]
  ├─ Request-phase plugin execution
  ├─ upstreamHttpClient.sendStream(...)            [UpstreamHttpClient.java:85]
  ├─ Multi<String> SSE chunks streamed to client
  └─ Response-phase plugins and logging executed after stream ends
```

For the detailed flow, see [02 Proxy Request Flow](../02-design/02-proxy-flow.en.md).

---

## Component Responsibilities

| Package/Class | Responsibility | Key Files |
|---|---|---|
| `api/` | REST endpoint definitions | `src/main/java/com/xenoamess/damning_proxy/api/HomeResource.java`, `src/main/java/com/xenoamess/damning_proxy/api/HealthResource.java`, `src/main/java/com/xenoamess/damning_proxy/api/admin/` |
| `proxy/` | OpenAI proxy core and upstream HTTP | `src/main/java/com/xenoamess/damning_proxy/proxy/ProxyApi.java`, `src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java`, `src/main/java/com/xenoamess/damning_proxy/proxy/UpstreamHttpClient.java`, `src/main/java/com/xenoamess/damning_proxy/proxy/CircuitBreaker.java`, `src/main/java/com/xenoamess/damning_proxy/proxy/RateLimiter.java` |
| `proxy/CircuitBreaker` | Upstream fault-tolerant circuit breaker | `src/main/java/com/xenoamess/damning_proxy/proxy/CircuitBreaker.java` |
| `proxy/RateLimiter` | Proxy request rate limiting | `src/main/java/com/xenoamess/damning_proxy/proxy/RateLimiter.java` |
| `plugin/` | Plugin context, executor, engines | `src/main/java/com/xenoamess/damning_proxy/plugin/PluginContext.java`, `src/main/java/com/xenoamess/damning_proxy/plugin/PluginExecutionService.java`, `src/main/java/com/xenoamess/damning_proxy/plugin/engine/` |
| `entity/` | JPA entities | `src/main/java/com/xenoamess/damning_proxy/entity/` |
| `repository/` | Data access interfaces and Panache implementations | `src/main/java/com/xenoamess/damning_proxy/repository/`, `src/main/java/com/xenoamess/damning_proxy/repository/panache/` |
| `service/` | Business services | `src/main/java/com/xenoamess/damning_proxy/service/TrafficLogService.java` |
| `filter/` | Global exception handling | `src/main/java/com/xenoamess/damning_proxy/filter/GlobalExceptionMapper.java` |
| `migration/` | Startup migration and sample data | `src/main/java/com/xenoamess/damning_proxy/migration/StartupMigration.java` |

---

## Frontend Architecture

```text
admin-web/src/
├── main.js           # Vue app entry
├── App.vue           # Sidebar layout
├── router.js         # Route configuration
├── style.css         # Global styles
├── api/              # API wrappers
│   ├── damning.js    # Admin API
│   └── chat.js       # Proxy endpoint API (including SSE)
├── components/       # Reusable components
│   └── CodeEditor.vue
└── views/            # Pages
    ├── Instances.vue
    ├── Profiles.vue
    ├── Plugins.vue
    ├── PluginGroups.vue
    ├── Chat.vue
    └── Logs.vue
```

The frontend build output goes to `src/main/resources/META-INF/resources/admin/`, served by Quarkus as static resources.

---

## Design Highlights

- **No Authentication**: All `/api/*` and `/v1/proxy/*` endpoints are publicly accessible; authentication relies solely on `profile.bearerToken` for upstream requests. If authentication is required, extend via a fronting reverse proxy or in a future version.
- **Instance Isolation**: Each `ProxyInstance` independently binds to one upstream Profile and one plugin group, accessed externally via `instanceSlug`.
- **H2 by Default**: File-based H2 is used for local development; production can switch to PostgreSQL/MySQL.
- **Upstream Forced HTTP/1.1**: `UpstreamHttpClient` disables ALPN and forces HTTP/1.1. Vert.x `HttpClient` may lose/truncate the response body when reading `response.body()` on worker threads under HTTP/2, causing non-streaming responses to become non-JSON (see traffic log #71).
- **Plugins Have Full JVM Permissions**: Both Groovy and GraalJS can execute arbitrary code; deploy with caution.

For security notes, see [04 Security Notes](../02-design/04-security-notes.en.md).
