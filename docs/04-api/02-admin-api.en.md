[中文版](02-admin-api.md)

# 02 Admin Endpoints

> Last updated: 2026-06-21  
> Source version: current workspace

Admin endpoints are prefixed with `/api`. All interfaces return JSON and are currently unauthenticated.

Code location: `src/main/java/com/xenoamess/damning_proxy/api/admin/`

---

## Upstream Profiles /api/profiles

`src/main/java/com/xenoamess/damning_proxy/api/admin/ProfileAdminApi.java:16`

`create()` and `update()` accept the `ProfileForm` record (rather than the `ProxyProfile` entity). The field mapping table is as follows.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/profiles` | List all Profiles |
| `GET` | `/api/profiles/{id}` | Get a single Profile |
| `POST` | `/api/profiles` | Create a Profile |
| `PUT` | `/api/profiles/{id}` | Update a Profile |
| `DELETE` | `/api/profiles/{id}` | Delete a Profile |

### Create/Update Request Body Example (ProfileForm)

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

| Field | Type | Constraints | Description |
|---|---|---|---|
| `name` | String | Required | Display name |
| `slug` | String | Required, unique | URL-friendly identifier |
| `baseUrl` | String | Required | Upstream API root URL |
| `bearerToken` | String | Optional | Authorization: Bearer token |
| `customHeaders` | String | Optional, JSON | Custom request headers as a JSON string |
| `customBody` | String | Optional, JSON | Default request body fields merged as a JSON string |
| `defaultModel` | String | Optional | Default model name |
| `timeoutMs` | Integer | Optional, default 600000 | Upstream timeout in milliseconds |
| `enabled` | boolean | Default true | Whether enabled |

---

## Instances /api/instances

`src/main/java/com/xenoamess/damning_proxy/api/admin/InstanceAdminApi.java:19`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/instances` | List all Instances |
| `GET` | `/api/instances/{id}` | Get a single Instance |
| `POST` | `/api/instances` | Create an Instance |
| `PUT` | `/api/instances/{id}` | Update an Instance |
| `DELETE` | `/api/instances/{id}` | Delete an Instance |
| `POST` | `/api/instances/export` | Export Instances (by `ids` or all) |
| `POST` | `/api/instances/import` | Import Instances |

### Create/Update Request Body Example

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

- `slug` is required and must be unique.
- `profileId` and `pluginGroupId` must exist.

### Export/Import Examples

`POST /api/instances/export`

```json
{
  "ids": [1, 2]
}
```

Response:

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

- During import, local IDs are resolved via `profileSlug` and `pluginGroupSlug`.
- If a `slug` already exists, the entry is skipped. Returns `{ "imported": n, "skipped": n }`.

---

## Plugins /api/plugins

`src/main/java/com/xenoamess/damning_proxy/api/admin/PluginAdminApi.java:16`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/plugins` | List all Plugins |
| `GET` | `/api/plugins/{id}` | Get a single Plugin |
| `POST` | `/api/plugins` | Create a Plugin |
| `PUT` | `/api/plugins/{id}` | Update a Plugin |
| `DELETE` | `/api/plugins/{id}` | Delete a Plugin |
| `POST` | `/api/plugins/export` | Export Plugins (by `ids` or all) |
| `POST` | `/api/plugins/import` | Import Plugins |

### Create/Update Request Body Example

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

- `language` values: `GROOVY` or `JS`.
- `executionPhase` values: `REQUEST`, `RESPONSE`, `BOTH`.
- `script` maximum length is 10000.

### Export/Import Examples

`POST /api/plugins/export`

```json
{
  "ids": [1, 2]
}
```

Returns an array of plugins.

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

- Deduplicated by `script`; if it already exists, it is skipped. Returns `{ "imported": n, "skipped": n }`.

---

## Plugin Groups /api/plugin-groups

`src/main/java/com/xenoamess/damning_proxy/api/admin/PluginGroupAdminApi.java:21`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/plugin-groups` | List all PluginGroups |
| `GET` | `/api/plugin-groups/{id}` | Get a single PluginGroup |
| `POST` | `/api/plugin-groups` | Create a PluginGroup |
| `PUT` | `/api/plugin-groups/{id}` | Update a PluginGroup |
| `DELETE` | `/api/plugin-groups/{id}` | Delete a PluginGroup |
| `POST` | `/api/plugin-groups/export` | Export PluginGroups (by `ids` or all) |
| `POST` | `/api/plugin-groups/import` | Import PluginGroups |

### Create/Update Request Body Example

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

- `slug` is required and must be unique.
- Each item in `items` references a `pluginId`.
- Ordering rules: `orderIndex` → `priority` → `id`.

### Export/Import Examples

`POST /api/plugin-groups/export`

```json
{
  "ids": [1, 2]
}
```

Response:

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

- During export, `pluginScript` is used in place of the local `pluginId` to facilitate cross-environment migration.
- During import, local plugins are looked up by `pluginScript`; items whose plugin cannot be found are ignored.
- If a `slug` already exists, the entry is skipped. Returns `{ "imported": n, "skipped": n }`.

---

## Logs /api/logs

`src/main/java/com/xenoamess/damning_proxy/api/admin/LogAdminApi.java:23`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/logs?limit=100&offset=0&profileId=&instanceId=` | List logs (paginated) |
| `GET` | `/api/logs/{id}` | Get raw log |
| `GET` | `/api/logs/{id}/friendly` | Get friendly-format log |
| `DELETE` | `/api/logs/{id}` | Delete a single log |
| `POST` | `/api/logs/clear` | Clear all logs |

### GET /api/logs Response Format

Returns a `PageResponse` object:

```json
{
  "items": [ ... ],
  "total": 150,
  "limit": 100,
  "offset": 0
}
```

### Query Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `limit` | int | 100 | Maximum number of returned items; maximum 1000 |
| `offset` | int | 0 | Pagination offset |
| `profileId` | Long | — | Filter by upstream Profile |
| `instanceId` | Long | — | Filter by Instance |

### Friendly Log Structure

`src/main/java/com/xenoamess/damning_proxy/dto/TrafficLogFriendlyDto.java`

The friendly log additionally extracts:

- `userPrompt`: user prompt
- `modelOutput`: model output
- `model`: requested model name
- `requestPipeline`: snapshot of request-phase plugin execution
- `responsePipeline`: snapshot of response-phase plugin execution

---

## Status Codes

| Status Code | Description |
|---|---|
| 200 | GET/PUT success |
| 201 | POST creation success |
| 204 | DELETE success |
| 400 | Parameter error (e.g., missing slug, profileId does not exist) |
| 404 | Resource not found |
| 409 | slug conflict |

---

## Frontend API Wrapper

The frontend uniformly wraps these APIs in `admin-web/src/api/damning.js`.
