[English Version](02-admin-api.en.md)

# 02 管理后台端点

> 最后更新：2026-07-07  
> 对应源码版本：当前工作区

管理后台端点前缀为 `/api`，所有接口均返回 JSON，当前无认证。

代码位置：`src/main/java/com/xenoamess/damning_proxy/api/admin/`

---

## 通用约定

- `slug` 字段格式统一为 `^[a-zA-Z0-9_-]+$`（字母、数字、下划线、短横线），导入时同样校验。
- 创建成功返回 `201`，更新成功返回 `200`，删除成功返回 `204`。
- 参数错误返回 `400`，资源不存在返回 `404`，`slug` 冲突返回 `409`。

---

## 上游配置 /api/profiles

`src/main/java/com/xenoamess/damning_proxy/api/admin/ProfileAdminApi.java:16`

create() 和 update() 接受 `ProfileForm` record（而非 `ProxyProfile` 实体），字段映射表如下。

| Method | Path | 说明 |
|---|---|---|
| `GET` | `/api/profiles` | 列出所有 Profile |
| `GET` | `/api/profiles/{id}` | 获取单个 Profile |
| `POST` | `/api/profiles` | 创建 Profile |
| `PUT` | `/api/profiles/{id}` | 更新 Profile |
| `DELETE` | `/api/profiles/{id}` | 删除 Profile |

### 创建/更新请求体示例（ProfileForm）

```json
{
  "name": "OpenAI",
  "slug": "openai",
  "baseUrl": "https://api.openai.com/v1",
  "bearerToken": "sk-xxxxxxxx",
  "customHeaders": "{\"X-Project\": \"demo\"}",
  "customBody": "{\"model\": \"gpt-4\"}",
  "defaultModel": "gpt-4",
  "timeoutMs": 30000,
  "enabled": true
}
```

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `name` | String | 非空 | 显示名称 |
| `slug` | String | 非空，唯一 | URL 友好标识 |
| `baseUrl` | String | 非空 | 上游 API 根地址 |
| `bearerToken` | String | 可空 | Authorization: Bearer Token |
| `customHeaders` | String | 可空，JSON | 自定义请求头，JSON 字符串 |
| `customBody` | String | 可空，JSON | 请求体默认字段合并，JSON 字符串 |
| `defaultModel` | String | 可空 | 默认模型名 |
| `timeoutMs` | Integer | 可空，默认 600000 | 上行超时毫秒 |
| `enabled` | boolean | 默认 true | 是否启用 |

---

## 实例 /api/instances

`src/main/java/com/xenoamess/damning_proxy/api/admin/InstanceAdminApi.java:19`

| Method | Path | 说明 |
|---|---|---|
| `GET` | `/api/instances` | 列出所有 Instance |
| `GET` | `/api/instances/{id}` | 获取单个 Instance |
| `POST` | `/api/instances` | 创建 Instance |
| `PUT` | `/api/instances/{id}` | 更新 Instance |
| `DELETE` | `/api/instances/{id}` | 删除 Instance |
| `POST` | `/api/instances/export` | 导出 Instance（按 `ids` 或全部） |
| `POST` | `/api/instances/import` | 导入 Instance |

### 创建/更新请求体示例

```json
{
  "name": "My Instance",
  "slug": "my-instance",
  "profileId": 1,
  "pluginGroupId": 1,
  "defaultModel": "gpt-4",
  "enabled": true
}
```

- `slug` 必填且唯一。
- `profileId` 和 `pluginGroupId` 必须存在。

### 导出/导入示例

`POST /api/instances/export`

```json
{
  "ids": [1, 2]
}
```

返回：

```json
[
  {
    "name": "My Instance",
    "slug": "my-instance",
    "profileSlug": "openai",
    "pluginGroupSlug": "default",
    "defaultModel": "gpt-4",
    "enabled": true
  }
]
```

`POST /api/instances/import`

```json
[
  {
    "name": "My Instance",
    "slug": "my-instance",
    "profileSlug": "openai",
    "pluginGroupSlug": "default",
    "defaultModel": "gpt-4",
    "enabled": true
  }
]
```

- 导入时通过 `profileSlug` 和 `pluginGroupSlug` 解析为本地 ID。
- `slug` 已存在则跳过，返回 `{ "imported": n, "skipped": n }`。

---

## 插件 /api/plugins

`src/main/java/com/xenoamess/damning_proxy/api/admin/PluginAdminApi.java:16`

| Method | Path | 说明 |
|---|---|---|
| `GET` | `/api/plugins` | 列出所有 Plugin |
| `GET` | `/api/plugins/{id}` | 获取单个 Plugin |
| `POST` | `/api/plugins` | 创建 Plugin（`multipart/form-data`） |
| `PUT` | `/api/plugins/{id}` | 更新 Plugin（`multipart/form-data`） |
| `DELETE` | `/api/plugins/{id}` | 删除 Plugin |
| `GET` | `/api/plugins/{id}/entries` | 列出 ZIP 包内文件 |
| `GET` | `/api/plugins/{id}/revisions` | 列出脚本历史版本 |
| `POST` | `/api/plugins/{id}/revisions/{revisionId}/rollback` | 回滚到指定历史版本 |
| `GET` | `/api/plugins/template` | 下载插件模板 ZIP |
| `POST` | `/api/plugins/export` | 导出 Plugin（按 `ids` 或全部） |
| `POST` | `/api/plugins/import` | 导入 Plugin（JSON 或 ZIP） |

### 创建/更新请求体字段（`multipart/form-data`）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `name` | String | 非空 | 显示名称 |
| `slug` | String | 非空，唯一 | URL 友好标识 |
| `description` | String | 可空 | 描述 |
| `language` | String | 非空 | `GROOVY` 或 `JS` |
| `executionPhase` | String | 非空 | `REQUEST`、`RESPONSE`、`BOTH` |
| `mode` | String | 默认 `SINGLE_SCRIPT` | `SINGLE_SCRIPT` 单脚本 或 `ZIP_PACKAGE` ZIP 包 |
| `script` | String | 可空，最大 10000 字符 | 脚本内容；`SINGLE_SCRIPT` 模式必填 |
| `enabled` | boolean | 默认 true | 是否启用 |
| `packageFile` | File | 可空 | `ZIP_PACKAGE` 模式上传的 ZIP 包；最大 `damning-proxy.plugin.zip.max-size-bytes`（默认 10 MiB） |

创建示例（cURL）：

```bash
curl -X POST http://localhost:12360/api/plugins \
  -F "name=Model Mapper" \
  -F "slug=model-mapper" \
  -F "language=JS" \
  -F "executionPhase=REQUEST" \
  -F "mode=SINGLE_SCRIPT" \
  -F "script=context.getRequestBody().model='gpt-4o';" \
  -F "enabled=true"
```

ZIP 包模式示例：

```bash
curl -X POST http://localhost:12360/api/plugins \
  -F "name=ZIP Plugin" \
  -F "slug=zip-plugin" \
  -F "language=GROOVY" \
  -F "executionPhase=BOTH" \
  -F "mode=ZIP_PACKAGE" \
  -F "enabled=true" \
  -F "packageFile=@plugin.zip"
```

- `language` 取值：`GROOVY` 或 `JS`。
- `executionPhase` 取值：`REQUEST`、`RESPONSE`、`BOTH`。
- `script` 最大长度 10000。

### 插件 ZIP 包文件列表

`GET /api/plugins/{id}/entries`

返回 ZIP 包内所有条目名列表：

```json
["main.groovy", "assets/info.txt"]
```

### 脚本历史版本

`GET /api/plugins/{id}/revisions`

返回脚本历史版本数组：

```json
[
  {
    "id": 1,
    "pluginId": 1,
    "script": "...",
    "createdAt": "2026-07-08T10:00:00"
  }
]
```

更新插件时，旧脚本会被自动快照为一条 revision。

### 回滚脚本

`POST /api/plugins/{id}/revisions/{revisionId}/rollback`

将当前脚本回滚到指定 revision，并先对当前脚本做快照，便于撤销回滚。

### 下载插件模板

`GET /api/plugins/template?language=JS&mode=ZIP_PACKAGE`

返回一个 ZIP 模板包，默认 `language=GROOVY`、`mode=SINGLE_SCRIPT`。

### 导出/导入示例

`POST /api/plugins/export`

```json
{
  "ids": [1, 2]
}
```

返回插件数组。

#### JSON 导入

`POST /api/plugins/import`（`Content-Type: application/json`）

```json
[
  {
    "name": "Model Mapper",
    "description": "",
    "language": "JS",
    "executionPhase": "REQUEST",
    "script": "...",
    "enabled": true
  }
]
```

- 按 `script` 去重，已存在则跳过，返回 `{ "imported": n, "skipped": n }`。

#### ZIP 导入

`POST /api/plugins/import`（`Content-Type: application/zip`）

上传由 `/api/plugins/export` 生成的 ZIP 包，内含 `manifest.json` 与各插件包。

- 单文件大小限制：`damning-proxy.plugin.import.max-zip-size`（默认 50 MiB）
- 单条目大小限制：`damning-proxy.plugin.import.max-entry-size`（默认 10 MiB）
- 条目数量限制：`damning-proxy.plugin.import.max-entries`（默认 100）
- 返回：`{ "imported": n, "skipped": n }`

---

## 插件组 /api/plugin-groups

`src/main/java/com/xenoamess/damning_proxy/api/admin/PluginGroupAdminApi.java:21`

| Method | Path | 说明 |
|---|---|---|
| `GET` | `/api/plugin-groups` | 列出所有 PluginGroup |
| `GET` | `/api/plugin-groups/{id}` | 获取单个 PluginGroup |
| `POST` | `/api/plugin-groups` | 创建 PluginGroup |
| `PUT` | `/api/plugin-groups/{id}` | 更新 PluginGroup |
| `DELETE` | `/api/plugin-groups/{id}` | 删除 PluginGroup |
| `POST` | `/api/plugin-groups/export` | 导出 PluginGroup（按 `ids` 或全部） |
| `POST` | `/api/plugin-groups/import` | 导入 PluginGroup |

### 创建/更新请求体示例

```json
{
  "name": "Default Group",
  "slug": "default",
  "description": "default group",
  "enabled": true,
  "items": [
    {
      "pluginId": 1,
      "orderIndex": 0,
      "priority": 0,
      "enabled": true
    }
  ]
}
```

- `slug` 必填且唯一。
- `items` 中每个 item 引用一个 `pluginId`。
- 排序规则：`orderIndex` → `priority` → `id`。

### 导出/导入示例

`POST /api/plugin-groups/export`

```json
{
  "ids": [1, 2]
}
```

返回：

```json
[
  {
    "name": "Default Group",
    "slug": "default",
    "description": "default group",
    "enabled": true,
    "items": [
      {
        "pluginScript": "context.log('demo')",
        "orderIndex": 0,
        "priority": 0,
        "enabled": true
      }
    ]
  }
]
```

`POST /api/plugin-groups/import`

```json
[
  {
    "name": "Default Group",
    "slug": "default",
    "description": "default group",
    "enabled": true,
    "items": [
      {
        "pluginScript": "context.log('demo')",
        "orderIndex": 0,
        "priority": 0,
        "enabled": true
      }
    ]
  }
]
```

- 导出时使用 `pluginScript` 代替本地 `pluginId`，便于跨环境迁移。
- 导入时按 `pluginScript` 查找本地插件，找不到的 item 会被忽略。
- `slug` 已存在则跳过，返回 `{ "imported": n, "skipped": n }`。

---

## 插件调试 /api/plugins/{id}/dry-run

`src/main/java/com/xenoamess/damning_proxy/api/admin/PluginDebugApi.java:25`

| Method | Path | 说明 |
|---|---|---|
| `POST` | `/api/plugins/{id}/dry-run` | 对指定插件执行一次模拟运行 |

### 请求体

```json
{
  "phase": "REQUEST",
  "instanceId": 1,
  "requestBody": { "model": "gpt-4", "messages": [{ "role": "user", "content": "hi" }] },
  "requestHeaders": { "X-Custom": "value" },
  "responseBody": { "choices": [] },
  "responseHeaders": {},
  "responseStatus": 200
}
```

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `phase` | String | 必填 | `REQUEST`、`RESPONSE`、`STREAM_CHUNK` |
| `instanceId` | Long | 可空 | 指定 Instance 以加载对应 Profile 的自定义 header/body |
| `requestBody` | Object | 可空 | 请求体 |
| `requestHeaders` | Object | 可空 | 请求头 |
| `responseBody` | Object | 可空 | 响应体 |
| `responseHeaders` | Object | 可空 | 响应头 |
| `responseStatus` | Integer | 可空 | 响应状态码 |

### 响应

```json
{
  "pluginName": "Model Mapper",
  "phase": "REQUEST",
  "pluginLogs": ["JS plugin executed, messages: 1"],
  "requestBody": { "model": "gpt-4o", "messages": [...] },
  "requestHeaders": { "Authorization": "Bearer ..." },
  "responseBody": null,
  "responseHeaders": {},
  "responseStatus": null,
  "stopped": false,
  "returned": false,
  "input": { "model": "gpt-4", "messages": [...] },
  "output": { "model": "gpt-4o", "messages": [...] },
  "error": false,
  "errorMessage": null
}
```

---

## 日志 /api/logs

`src/main/java/com/xenoamess/damning_proxy/api/admin/LogAdminApi.java:23`

| Method | Path | 说明 |
|---|---|---|
| `GET` | `/api/logs?limit=100&offset=0&profileId=&instanceId=&status=&path=&startTime=&endTime=` | 列出日志（分页、筛选） |
| `GET` | `/api/logs/export?format=json&<filters>` | 导出当前筛选结果（JSON 或 CSV） |
| `GET` | `/api/logs/{id}` | 获取原始日志 |
| `GET` | `/api/logs/{id}/friendly` | 获取友好格式日志 |
| `DELETE` | `/api/logs/{id}` | 删除单条日志 |
| `POST` | `/api/logs/clear` | 清空所有日志 |
| `POST` | `/api/logs/prune` | 批量清理日志 |

### GET /api/logs 响应格式

返回 `PageResponse` 对象：

```json
{
  "items": [ ... ],
  "total": 150,
  "limit": 100,
  "offset": 0
}
```

### 查询参数

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `limit` | int | 100 | 最大返回条数，最大 1000 |
| `offset` | int | 0 | 分页偏移量 |
| `profileId` | Long | — | 按上游 Profile 筛选 |
| `instanceId` | Long | — | 按 Instance 筛选 |
| `status` | String | — | 状态筛选：`success`（2xx/空状态）或 `error`（>=400） |
| `path` | String | — | 请求路径关键字模糊匹配 |
| `startTime` | String | — | 开始时间，格式 `YYYY-MM-DDTHH:mm:ss` |
| `endTime` | String | — | 结束时间，格式 `YYYY-MM-DDTHH:mm:ss` |

### 日志导出

`GET /api/logs/export?format=json&profileId=1&instanceId=1&status=error&startTime=...&endTime=...`

- `format` 取值：`json`（默认）或 `csv`。
- 支持 `GET /api/logs` 的所有筛选参数。
- 默认最多导出 10000 条。
- JSON 返回原始日志数组；CSV 包含表头与字段转义。

---

### 友好日志结构

`src/main/java/com/xenoamess/damning_proxy/dto/TrafficLogFriendlyDto.java`

友好日志额外提取：

- `userPrompt`：用户提示词
- `modelOutput`：模型输出
- `model`：请求模型名
- `promptTokens` / `completionTokens` / `totalTokens`：上游返回的 token 用量
- `requestPipeline`：请求阶段插件执行快照
- `responsePipeline`：响应阶段插件执行快照

---

## 数据库 /api/admin/database

`src/main/java/com/xenoamess/damning_proxy/api/admin/DatabaseAdminApi.java:17`

| Method | Path | 说明 |
|---|---|---|
| `POST` | `/api/admin/database/backup?name=...` | 执行 H2 热备份到 `~/.damning-proxy/backups/` |
| `POST` | `/api/admin/database/restore?path=...` | 验证并暂存恢复文件，返回重启命令 |

### 备份

```bash
curl -X POST http://localhost:12360/api/admin/database/backup
```

响应：

```json
{
  "path": "/home/xxx/.damning-proxy/backups/backup_20260707_123456.zip",
  "success": true
}
```

- 默认使用 `backup_YYYYMMDD_HHmmss.zip` 文件名。
- `name` 参数可指定文件名，但不能包含路径分隔符。
- 内存数据库（`jdbc:h2:mem:`）不支持热备份，会返回 500 错误。

### 恢复

```bash
curl -X POST "http://localhost:12360/api/admin/database/restore?path=/home/xxx/.damning-proxy/backups/backup_20260707_123456.zip"
```

响应：

```json
{
  "stagedPath": "/home/xxx/.damning-proxy/data.restore.zip",
  "restartCommand": "Stop the application, then run: ..."
}
```

- 恢复需要先停止应用，再解压 `data.restore.zip` 覆盖当前数据库文件，然后重启。
- 当前实现暂不支持在线热恢复，因为 H2 文件数据库在运行时被锁定。

---

## 指标 /api/metrics

`src/main/java/com/xenoamess/damning_proxy/api/admin/MetricsAdminApi.java:16`

| Method | Path | 说明 |
|---|---|---|
| `GET` | `/api/metrics/summary?startTime=&endTime=` | 汇总指标（总请求、错误、平均延迟、token） |
| `GET` | `/api/metrics/time-series?startTime=&endTime=&bucketMinutes=` | 按时间桶聚合的请求、错误、延迟、token |
| `GET` | `/api/metrics/top-instances?startTime=&endTime=&limit=` | 请求量最高的实例 |
| `GET` | `/api/metrics/status-distribution?startTime=&endTime=` | 成功/错误分布 |

- 时间参数格式：`YYYY-MM-DDTHH:mm:ss`。
- 省略时间参数时，默认查询最近 24 小时。
- `bucketMinutes` 默认值 60，按小时聚合；跨天时按天聚合。

---

## 限流设置 /api/settings/rate-limit

`src/main/java/com/xenoamess/damning_proxy/api/admin/GlobalSettingsAdminApi.java:11`

| Method | Path | 说明 |
|---|---|---|
| `GET` | `/api/settings/rate-limit` | 获取当前限流设置 |
| `PUT` | `/api/settings/rate-limit` | 更新限流设置 |

### 请求/响应体

```json
{
  "id": 1,
  "maxRequestsPerWindow": 100,
  "windowSeconds": 60,
  "createdAt": "2026-07-08T10:00:00",
  "updatedAt": "2026-07-08T10:00:00"
}
```

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `maxRequestsPerWindow` | Integer | 必填，1-1000000 | 每个时间窗口内允许的最大请求数 |
| `windowSeconds` | Integer | 必填，1-86400 | 限流时间窗口秒数 |

更新后，由于全局设置缓存，新规则最多延迟 `damning-proxy.global-settings.cache-ttl-seconds`（默认 60 秒）生效。

---

## 状态码

| 状态码 | 说明 |
|---|---|
| 200 | GET/PUT 成功 |
| 201 | POST 创建成功 |
| 204 | DELETE 成功 |
| 400 | 参数错误（如缺少 slug、profileId 不存在） |
| 404 | 资源不存在 |
| 409 | slug 冲突 |

---

## 前端 API 封装

前端统一封装在 `admin-web/src/api/damning.js`。
