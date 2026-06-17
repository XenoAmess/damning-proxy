# 01 数据模型

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区

## 实体清单

| 实体 | 表名 | 文件 | 说明 |
|---|---|---|---|
| ProxyProfile | proxy_profile | `src/main/java/com/xenoamess/damning_proxy/entity/ProxyProfile.java` | 上游 OpenAI 源配置 |
| ProxyInstance | proxy_instance | `src/main/java/com/xenoamess/damning_proxy/entity/ProxyInstance.java` | 对外暴露的代理实例 |
| Plugin | plugin | `src/main/java/com/xenoamess/damning_proxy/entity/Plugin.java` | 插件脚本 |
| PluginGroup | plugin_group | `src/main/java/com/xenoamess/damning_proxy/entity/PluginGroup.java` | 插件组 |
| PluginGroupItem | plugin_group_item | `src/main/java/com/xenoamess/damning_proxy/entity/PluginGroupItem.java` | 插件组与插件的多对多关系 |
| TrafficLog | traffic_log | `src/main/java/com/xenoamess/damning_proxy/entity/TrafficLog.java` | 流量日志 |

---

## ProxyProfile（上游配置）

`src/main/java/com/xenoamess/damning_proxy/entity/ProxyProfile.java:12`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | Long | PK，自增 | 主键 |
| name | String | 非空 | 显示名称 |
| slug | String | 非空，唯一 | URL 友好标识 |
| baseUrl | String | 非空 | 上游 API 根地址，如 `https://api.openai.com/v1` |
| bearerToken | String | 可空 | 上行 Authorization: Bearer Token |
| customHeaders | String | 可空，长度 4000 | JSON 格式自定义请求头 |
| defaultModel | String | 可空 | 默认模型名 |
| timeoutMs | Integer | 默认 30000 | 上行请求超时毫秒 |
| enabled | boolean | 默认 true | 是否启用 |
| createdAt | LocalDateTime | 自动 | 创建时间 |
| updatedAt | LocalDateTime | 自动 | 更新时间 |

---

## ProxyInstance（代理实例）

`src/main/java/com/xenoamess/damning_proxy/entity/ProxyInstance.java:12`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | Long | PK，自增 | 主键 |
| name | String | 非空 | 显示名称 |
| slug | String | 非空，唯一 | 外部访问路径 `/{slug}/...` |
| profileId | Long | 非空 | 关联的上游 Profile ID |
| pluginGroupId | Long | 非空 | 关联的插件组 ID |
| defaultModel | String | 可空 | 该实例默认模型 |
| enabled | boolean | 默认 true | 是否启用 |
| createdAt | LocalDateTime | 自动 | 创建时间 |
| updatedAt | LocalDateTime | 自动 | 更新时间 |

实例是外部访问的入口。例如 `slug = my-instance`，则代理端点为：

```text
GET  /v1/proxy/my-instance/models
POST /v1/proxy/my-instance/chat/completions
```

---

## Plugin（插件）

`src/main/java/com/xenoamess/damning_proxy/entity/Plugin.java:12`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | Long | PK，自增 | 主键 |
| name | String | 非空 | 插件名称 |
| description | String | 可空，长度 2000 | 描述 |
| language | Language 枚举 | 非空 | `GROOVY` 或 `JS` |
| script | String | 非空，长度 10000 | 插件脚本内容 |
| executionPhase | ExecutionPhase 枚举 | 非空，默认 `BOTH` | `REQUEST` / `RESPONSE` / `BOTH` |
| enabled | boolean | 默认 true | 是否启用 |
| createdAt | LocalDateTime | 自动 | 创建时间 |
| updatedAt | LocalDateTime | 自动 | 更新时间 |

### 枚举定义

```java
public enum Language {
    GROOVY,
    JS
}

public enum ExecutionPhase {
    REQUEST,
    RESPONSE,
    BOTH
}
```

`src/main/java/com/xenoamess/damning_proxy/entity/Plugin.java:49`

---

## PluginGroup（插件组）

`src/main/java/com/xenoamess/damning_proxy/entity/PluginGroup.java:15`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | Long | PK，自增 | 主键 |
| name | String | 非空 | 插件组名称 |
| slug | String | 非空，唯一 | URL 友好标识 |
| description | String | 可空，长度 2000 | 描述 |
| enabled | boolean | 默认 true | 是否启用 |
| items | List<PluginGroupItem> | 级联 ALL，orphanRemoval | 插件组项列表 |
| createdAt | LocalDateTime | 自动 | 创建时间 |
| updatedAt | LocalDateTime | 自动 | 更新时间 |

### 排序规则

`sortedItems()` 按以下顺序排序：

1. `orderIndex` 升序
2. `priority` 升序
3. `id` 升序

`src/main/java/com/xenoamess/damning_proxy/entity/PluginGroup.java:47`

---

## PluginGroupItem（插件组项）

`src/main/java/com/xenoamess/damning_proxy/entity/PluginGroupItem.java:12`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | Long | PK，自增 | 主键 |
| group | PluginGroup | 非空 | 所属插件组 |
| plugin | Plugin | 非空 | 引用的插件 |
| orderIndex | Integer | 非空，默认 0 | 执行顺序 |
| priority | Integer | 非空，默认 0 | 优先级 |
| enabled | boolean | 默认 true | 是否启用 |
| createdAt | LocalDateTime | 自动 | 创建时间 |

---

## TrafficLog（流量日志）

`src/main/java/com/xenoamess/damning_proxy/entity/TrafficLog.java:11`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | Long | PK，自增 | 主键 |
| instanceId | Long | 可空 | 实例 ID |
| instanceSlug | String | 可空 | 实例 slug |
| profileId | Long | 可空 | 上游 Profile ID |
| requestPath | String | 可空 | 请求路径 |
| requestMethod | String | 可空 | 请求方法 |
| requestHeaders | String | 可空，长度 4000 | JSON 格式请求头 |
| requestBody | String | 可空，长度 20000 | JSON 格式请求体 |
| requestTime | LocalDateTime | 自动 | 请求时间 |
| responseStatus | Integer | 可空 | 响应状态码 |
| responseHeaders | String | 可空，长度 4000 | JSON 格式响应头 |
| responseBody | String | 可空，长度 20000 | JSON 格式响应体 |
| responseTime | LocalDateTime | 可空 | 响应时间 |
| durationMs | Long | 可空 | 请求耗时毫秒 |
| pluginLogs | String | 可空，长度 10000 | JSON 数组，插件日志 |
| friendlyPluginSnapshots | String | 可空，长度 10000 | JSON 数组，插件执行快照 |

### 长度截断

`TrafficLogService` 在序列化时会对以下字段做截断：

| 字段 | 最大长度 |
|---|---|
| requestHeaders / responseHeaders | 2000 |
| requestBody / responseBody | 10000 |
| pluginLogs | 5000 |
| friendlyPluginSnapshots | 8000 |

超出后会追加 `...[truncated]`。

`src/main/java/com/xenoamess/damning_proxy/service/TrafficLogService.java:27`

---

## 实体关系图

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
    │                                   N  Plugin
    │
    └── 通过 instanceId/profileId 被 TrafficLog 引用
```

- 一个 Profile 可被多个 Instance 使用。
- 一个 Instance 使用一个 PluginGroup。
- 一个 PluginGroup 包含多个 PluginGroupItem，每个 item 引用一个 Plugin。
- TrafficLog 同时记录 instanceId 与 profileId，便于按实例或按上游筛选。
