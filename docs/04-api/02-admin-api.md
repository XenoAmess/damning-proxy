[English Version](02-admin-api.en.md)

# 02 管理后台端点

> 最后更新：2026-06-21  
> 对应源码版本：当前工作区

管理后台端点前缀为 `/api`，所有接口均返回 JSON，当前无认证。

代码位置：`src/main/java/com/xenoamess/damning_proxy/api/admin/`

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
| `POST` | `/api/plugins` | 创建 Plugin |
| `PUT` | `/api/plugins/{id}` | 更新 Plugin |
| `DELETE` | `/api/plugins/{id}` | 删除 Plugin |
| `POST` | `/api/plugins/export` | 导出 Plugin（按 `ids` 或全部） |
| `POST` | `/api/plugins/import` | 导入 Plugin |

### 创建/更新请求体示例

```json
{
  "name": "Model Mapper",
  "description": "Rewrite model name",
  "language": "JS",
  "script": "var body = context.getRequestBody(); body.model = 'gpt-4o'; context.setRequestBody(body);",
  "executionPhase": "REQUEST",
  "enabled": true
}
```

- `language` 取值：`GROOVY` 或 `JS`。
- `executionPhase` 取值：`REQUEST`、`RESPONSE`、`BOTH`。
- `script` 最大长度 10000。

### 导出/导入示例

`POST /api/plugins/export`

```json
{
  "ids": [1, 2]
}
```

返回插件数组。

`POST /api/plugins/import`

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

## 日志 /api/logs

`src/main/java/com/xenoamess/damning_proxy/api/admin/LogAdminApi.java:23`

| Method | Path | 说明 |
|---|---|---|
| `GET` | `/api/logs?limit=100&offset=0&profileId=&instanceId=` | 列出日志（分页） |
| `GET` | `/api/logs/{id}` | 获取原始日志 |
| `GET` | `/api/logs/{id}/friendly` | 获取友好格式日志 |
| `DELETE` | `/api/logs/{id}` | 删除单条日志 |
| `POST` | `/api/logs/clear` | 清空所有日志 |

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

### 友好日志结构

`src/main/java/com/xenoamess/damning_proxy/dto/TrafficLogFriendlyDto.java`

友好日志额外提取：

- `userPrompt`：用户提示词
- `modelOutput`：模型输出
- `model`：请求模型名
- `requestPipeline`：请求阶段插件执行快照
- `responsePipeline`：响应阶段插件执行快照

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
