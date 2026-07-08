[中文版](01-data-model.md)

# 01 Data Model

> Last updated: 2026-07-08  
> Corresponding source version: current workspace

## Entity List

| Entity | Table Name | File | Description |
|---|---|---|---|
| ProxyProfile | proxy_profile | `src/main/java/com/xenoamess/damning_proxy/entity/ProxyProfile.java` | Upstream OpenAI source configuration |
| ProxyInstance | proxy_instance | `src/main/java/com/xenoamess/damning_proxy/entity/ProxyInstance.java` | Exposed proxy instance |
| Plugin | plugin | `src/main/java/com/xenoamess/damning_proxy/entity/Plugin.java` | Plugin script |
| PluginGroup | plugin_group | `src/main/java/com/xenoamess/damning_proxy/entity/PluginGroup.java` | Plugin group |
| PluginGroupItem | plugin_group_item | `src/main/java/com/xenoamess/damning_proxy/entity/PluginGroupItem.java` | Many-to-many relationship between plugin groups and plugins |
| PluginScriptRevision | plugin_script_revision | `src/main/java/com/xenoamess/damning_proxy/entity/PluginScriptRevision.java` | Plugin script revision history |
| GlobalSettings | global_settings | `src/main/java/com/xenoamess/damning_proxy/entity/GlobalSettings.java` | Global settings (rate limiting, etc.) |
| TrafficLog | traffic_log | `src/main/java/com/xenoamess/damning_proxy/entity/TrafficLog.java` | Traffic log |

---

## ProxyProfile (Upstream Profile)

`src/main/java/com/xenoamess/damning_proxy/entity/ProxyProfile.java:12`

| Field | Type | Constraint | Description |
|---|---|---|---|
| id | Long | PK, auto-increment | Primary key |
| name | String | Not null | Display name |
| slug | String | Not null, unique | URL-friendly identifier |
| baseUrl | String | Not null | Upstream API root address, e.g. `https://api.openai.com/v1` |
| bearerToken | String | Nullable | Upstream Authorization: Bearer Token |
| customHeaders | String | Nullable, length 4000 | Custom request headers in JSON format |
| customBody | String | Nullable, length 10000 | Default request body fields merged in JSON format |
| defaultModel | String | Nullable | Default model name |
| timeoutMs | Integer | Default 600000 | Upstream request timeout in milliseconds |
| circuitBreakerFailureThreshold | Integer | Default 5 | Consecutive failures before opening the circuit breaker |
| circuitBreakerOpenTimeoutSeconds | Integer | Default 60 | Seconds to wait before half-opening the circuit breaker |
| enabled | boolean | Default true | Whether enabled |
| createdAt | LocalDateTime | Auto | Creation time |
| updatedAt | LocalDateTime | Auto | Update time |

---

## ProxyInstance (Proxy Instance)

`src/main/java/com/xenoamess/damning_proxy/entity/ProxyInstance.java:12`

| Field | Type | Constraint | Description |
|---|---|---|---|
| id | Long | PK, auto-increment | Primary key |
| name | String | Not null | Display name |
| slug | String | Not null, unique | External access path `/{slug}/...` |
| profileId | Long | Not null | Associated upstream Profile ID |
| pluginGroupId | Long | Not null | Associated plugin group ID |
| defaultModel | String | Nullable | Default model for this instance |
| enabled | boolean | Default true | Whether enabled |
| createdAt | LocalDateTime | Auto | Creation time |
| updatedAt | LocalDateTime | Auto | Update time |

The instance is the entry point for external access. For example, if `slug = my-instance`, the proxy endpoints are:

```text
GET  /v1/proxy/my-instance/models
POST /v1/proxy/my-instance/chat/completions
POST /v1/proxy/my-instance/embeddings
POST /v1/proxy/my-instance/images/generations
POST /v1/proxy/my-instance/audio/transcriptions
POST /v1/proxy/my-instance/audio/translations
POST /v1/proxy/my-instance/audio/speech
```

---

## Plugin

`src/main/java/com/xenoamess/damning_proxy/entity/Plugin.java:12`

| Field | Type | Constraint | Description |
|---|---|---|---|
| id | Long | PK, auto-increment | Primary key |
| name | String | Not null | Plugin name |
| description | String | Nullable, length 2000 | Description |
| language | Language enum | Not null | `GROOVY` or `JS` |
| script | String | Not null, length 10000 | Plugin script content; required for `SINGLE_SCRIPT` mode |
| mode | Mode enum | Default `SINGLE_SCRIPT` | `SINGLE_SCRIPT` or `ZIP_PACKAGE` |
| packagePath | String | Nullable | Package storage path for `ZIP_PACKAGE` mode |
| sample | boolean | Default false | Whether this is a sample plugin (sample plugins cannot be modified) |
| executionPhase | ExecutionPhase enum | Not null, default `BOTH` | `REQUEST` / `RESPONSE` / `BOTH` |
| enabled | boolean | Default true | Whether enabled |
| createdAt | LocalDateTime | Auto | Creation time |
| updatedAt | LocalDateTime | Auto | Update time |

### Enum Definitions

```java
public enum Language {
    GROOVY,
    JS
}

public enum Mode {
    SINGLE_SCRIPT,
    ZIP_PACKAGE
}

public enum ExecutionPhase {
    REQUEST,
    RESPONSE,
    BOTH
}
```

`src/main/java/com/xenoamess/damning_proxy/entity/Plugin.java:49`

---

## PluginGroup

`src/main/java/com/xenoamess/damning_proxy/entity/PluginGroup.java:15`

| Field | Type | Constraint | Description |
|---|---|---|---|
| id | Long | PK, auto-increment | Primary key |
| name | String | Not null | Plugin group name |
| slug | String | Not null, unique | URL-friendly identifier |
| description | String | Nullable, length 2000 | Description |
| enabled | boolean | Default true | Whether enabled |
| items | List<PluginGroupItem> | Cascade ALL, orphanRemoval | Plugin group item list |
| createdAt | LocalDateTime | Auto | Creation time |
| updatedAt | LocalDateTime | Auto | Update time |

### Sorting Rules

`sortedItems()` sorts in the following order:

1. `orderIndex` ascending
2. `priority` ascending
3. `id` ascending

`src/main/java/com/xenoamess/damning_proxy/entity/PluginGroup.java:47`

---

## PluginGroupItem

`src/main/java/com/xenoamess/damning_proxy/entity/PluginGroupItem.java:12`

| Field | Type | Constraint | Description |
|---|---|---|---|
| id | Long | PK, auto-increment | Primary key |
| group | PluginGroup | Not null | Belongs to plugin group |
| plugin | Plugin | Not null | Referenced plugin |
| orderIndex | Integer | Not null, default 0 | Execution order |
| priority | Integer | Not null, default 0 | Priority |
| enabled | boolean | Default true | Whether enabled |
| createdAt | LocalDateTime | Auto | Creation time |

---

## PluginScriptRevision (Plugin Script Revision)

`src/main/java/com/xenoamess/damning_proxy/entity/PluginScriptRevision.java:12`

| Field | Type | Constraint | Description |
|---|---|---|---|
| id | Long | PK, auto-increment | Primary key |
| pluginId | Long | Not null | Owning plugin ID |
| script | String | Not null, Lob | Script content at the time of the snapshot |
| createdAt | LocalDateTime | Auto | Snapshot time |

Each plugin script update automatically saves the old script as a `PluginScriptRevision`. It can be rolled back via `/api/plugins/{id}/revisions/{revisionId}/rollback`.

---

## GlobalSettings (Global Settings)

`src/main/java/com/xenoamess/damning_proxy/entity/GlobalSettings.java:12`

| Field | Type | Constraint | Description |
|---|---|---|---|
| id | Long | PK, auto-increment | Primary key |
| maxRequestsPerWindow | Integer | Default 100 | Max requests allowed in a rate-limit window |
| windowSeconds | Integer | Default 60 | Rate-limit window in seconds |
| createdAt | LocalDateTime | Auto | Creation time |
| updatedAt | LocalDateTime | Auto | Update time |

Single-row table. `GlobalSettingsAdminApi` exposes `/api/settings/rate-limit` for reading and writing. To reduce H2 queries, `PanacheGlobalSettingsRepository` caches the object with a default TTL of 60 seconds.

---

## TrafficLog (Traffic Log)

`src/main/java/com/xenoamess/damning_proxy/entity/TrafficLog.java:11`

| Field | Type | Constraint | Description |
|---|---|---|---|
| id | Long | PK, auto-increment | Primary key |
| instanceId | Long | Nullable | Instance ID |
| instanceSlug | String | Nullable | Instance slug |
| profileId | Long | Nullable | Upstream Profile ID |
| requestPath | String | Nullable | Request path |
| requestMethod | String | Nullable | Request method |
| requestHeaders | String | Nullable, length 4000 | Request headers in JSON format |
| requestBody | String | Nullable, Lob | Request body in JSON format |
| requestBodyLength | Integer | Nullable | Original request body length before truncation |
| upstreamBaseUrl | String | Nullable | Upstream request baseUrl |
| timeoutMs | Integer | Nullable | Upstream request timeout in milliseconds |
| streaming | Boolean | Nullable | Whether it is a streaming request |
| requestTime | LocalDateTime | Manual | Request time (explicitly written by `TrafficLogService.recordRequest()`) |
| responseStatus | Integer | Nullable | Response status code |
| responseHeaders | String | Nullable, length 4000 | Response headers in JSON format |
| responseBody | String | Nullable, Lob | Response body in JSON format |
| responseBodyLength | Integer | Nullable | Original response body length before truncation |
| errorMessage | String | Nullable, length 2000 | Upstream request error message |
| promptTokens | Integer | Nullable | `usage.prompt_tokens` from upstream |
| completionTokens | Integer | Nullable | `usage.completion_tokens` from upstream |
| totalTokens | Integer | Nullable | `usage.total_tokens` from upstream |
| responseTime | LocalDateTime | Nullable | Response time |
| durationMs | Long | Nullable | Request duration in milliseconds |
| pluginLogs | String | Nullable, Lob | JSON array of plugin logs |
| friendlyPluginSnapshots | String | Nullable, Lob | JSON array of plugin execution snapshots |

### Length Truncation

`TrafficLogService` truncates the following fields during serialization. The maximum length of each field is controlled by configuration items:

| Field | Configuration Item | Default Max Length |
|---|---|---|
| requestHeaders / responseHeaders | `damning-proxy.log.max-headers-length` | 2000 |
| requestBody / responseBody | `damning-proxy.log.max-body-length` | 1073741824 |
| pluginLogs | `damning-proxy.log.max-plugin-logs-length` | 5000 |
| friendlyPluginSnapshots | `damning-proxy.log.max-friendly-snapshots-length` | 8000 |

When exceeded, it truncates and appends `...[truncated]`; `requestBodyLength` / `responseBodyLength` retain the original length before truncation.

`src/main/java/com/xenoamess/damning_proxy/service/TrafficLogService.java:27`

---

## Entity Relationship Diagram

```text
ProxyProfile  1 ──────< N  ProxyInstance
    │                         │
    │                         │ 1
    │                         │
    │                         N  PluginGroup
    │                              │ 1
    │                              │
    │                              N  PluginGroupItem
    │                                   │ N
    │                                   │
    │                                   N  Plugin  1 ──────< N  PluginScriptRevision
    │
    │                       GlobalSettings (singleton)
    │
    └── Referenced by TrafficLog via instanceId/profileId
```

- One Profile can be used by multiple Instances.
- One Instance uses one PluginGroup.
- One PluginGroup contains multiple PluginGroupItems, each item references one Plugin.
- One Plugin can have multiple PluginScriptRevision history entries.
- GlobalSettings is a single-row table for global configuration (e.g., rate limiting).
- TrafficLog records both instanceId and profileId for filtering by instance or upstream.
