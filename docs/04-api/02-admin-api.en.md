[中文版](02-admin-api.md)

# 02 Admin Endpoints

> Last updated: 2026-07-07  
> Source version: current workspace

Admin endpoints are prefixed with `/api`. All interfaces return JSON and are currently unauthenticated.

Code location: `src/main/java/com/xenoamess/damning_proxy/api/admin/`

---

## Common Conventions

- `slug` fields follow the `^[a-zA-Z0-9_-]+$` regex and are validated during import.
- `201` for successful creation, `200` for successful GET/PUT, `204` for successful DELETE.
- `400` for parameter errors, `404` for missing resources, `409` for slug conflicts.

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
| `POST` | `/api/plugins` | Create a Plugin (`multipart/form-data`) |
| `PUT` | `/api/plugins/{id}` | Update a Plugin (`multipart/form-data`) |
| `DELETE` | `/api/plugins/{id}` | Delete a Plugin |
| `GET` | `/api/plugins/{id}/entries` | List files inside the plugin ZIP package |
| `GET` | `/api/plugins/{id}/revisions` | List script revision history |
| `POST` | `/api/plugins/{id}/revisions/{revisionId}/rollback` | Roll back to a specific revision |
| `GET` | `/api/plugins/template` | Download a plugin template ZIP |
| `POST` | `/api/plugins/export` | Export Plugins (by `ids` or all) |
| `POST` | `/api/plugins/import` | Import Plugins (JSON or ZIP) |

### Create/Update Request Body Fields (`multipart/form-data`)

| Field | Type | Constraints | Description |
|---|---|---|---|
| `name` | String | Required | Display name |
| `slug` | String | Required, unique | URL-friendly identifier |
| `description` | String | Optional | Description |
| `language` | String | Required | `GROOVY` or `JS` |
| `executionPhase` | String | Required | `REQUEST`, `RESPONSE`, or `BOTH` |
| `mode` | String | Default `SINGLE_SCRIPT` | `SINGLE_SCRIPT` or `ZIP_PACKAGE` |
| `script` | String | Optional, max 10000 chars | Script content; required for `SINGLE_SCRIPT` mode |
| `enabled` | boolean | Default true | Whether enabled |
| `packageFile` | File | Optional | ZIP package for `ZIP_PACKAGE` mode; max `damning-proxy.plugin.zip.max-size-bytes` (default 10 MiB) |

Create example (cURL):

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

ZIP package mode example:

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

- `language` values: `GROOVY` or `JS`.
- `executionPhase` values: `REQUEST`, `RESPONSE`, `BOTH`.
- `script` maximum length is 10000.

### Plugin ZIP Package Entries

`GET /api/plugins/{id}/entries`

Returns the list of file names inside the plugin ZIP package:

```json
["main.groovy", "assets/info.txt"]
```

### Script Revisions

`GET /api/plugins/{id}/revisions`

Returns the script revision history:

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

Updating a plugin automatically snapshots the old script as a revision.

### Rollback Script

`POST /api/plugins/{id}/revisions/{revisionId}/rollback`

Rolls the plugin script back to the specified revision. The current script is snapshotted first so the rollback can be undone.

### Download Plugin Template

`GET /api/plugins/template?language=JS&mode=ZIP_PACKAGE`

Returns a ZIP template package. Defaults are `language=GROOVY` and `mode=SINGLE_SCRIPT`.

### Export/Import Examples

`POST /api/plugins/export`

```json
{
  "ids": [1, 2]
}
```

Returns an array of plugins.

#### JSON Import

`POST /api/plugins/import` (`Content-Type: application/json`)

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

#### ZIP Import

`POST /api/plugins/import` (`Content-Type: application/zip`)

Upload the ZIP produced by `/api/plugins/export`, which contains `manifest.json` and plugin packages.

- Max ZIP size: `damning-proxy.plugin.import.max-zip-size` (default 50 MiB)
- Max entry size: `damning-proxy.plugin.import.max-entry-size` (default 10 MiB)
- Max entries: `damning-proxy.plugin.import.max-entries` (default 100)
- Returns: `{ "imported": n, "skipped": n }`

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

## Plugin Debug /api/plugins/{id}/dry-run

`src/main/java/com/xenoamess/damning_proxy/api/admin/PluginDebugApi.java:25`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/plugins/{id}/dry-run` | Run a plugin once in isolation |

### Request Body

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

| Field | Type | Constraints | Description |
|---|---|---|---|
| `phase` | String | Required | `REQUEST`, `RESPONSE`, or `STREAM_CHUNK` |
| `instanceId` | Long | Optional | Load the associated Profile's custom headers/body |
| `requestBody` | Object | Optional | Request body |
| `requestHeaders` | Object | Optional | Request headers |
| `responseBody` | Object | Optional | Response body |
| `responseHeaders` | Object | Optional | Response headers |
| `responseStatus` | Integer | Optional | Response status code |

### Response

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

## Logs /api/logs

`src/main/java/com/xenoamess/damning_proxy/api/admin/LogAdminApi.java:23`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/logs?limit=100&offset=0&profileId=&instanceId=&status=&path=&startTime=&endTime=` | List logs (paginated, filtered) |
| `GET` | `/api/logs/export?format=json&<filters>` | Export current filtered results (JSON or CSV) |
| `GET` | `/api/logs/{id}` | Get raw log |
| `GET` | `/api/logs/{id}/friendly` | Get friendly-format log |
| `DELETE` | `/api/logs/{id}` | Delete a single log |
| `POST` | `/api/logs/clear` | Clear all logs |
| `POST` | `/api/logs/prune` | Bulk prune logs |

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
| `status` | String | — | Status filter: `success` (2xx/empty status) or `error` (>=400) |
| `path` | String | — | Fuzzy match on request path |
| `startTime` | String | — | Start time, format `YYYY-MM-DDTHH:mm:ss` |
| `endTime` | String | — | End time, format `YYYY-MM-DDTHH:mm:ss` |

### Friendly Log Structure

`src/main/java/com/xenoamess/damning_proxy/dto/TrafficLogFriendlyDto.java`

The friendly log additionally extracts:

- `userPrompt`: user prompt
- `modelOutput`: model output
- `model`: requested model name
- `promptTokens` / `completionTokens` / `totalTokens`: token usage returned by upstream
- `requestPipeline`: snapshot of request-phase plugin execution
- `responsePipeline`: snapshot of response-phase plugin execution

---

### Log Export

`GET /api/logs/export?format=json&profileId=1&instanceId=1&status=error&startTime=...&endTime=...`

- `format` values: `json` (default) or `csv`.
- Supports all filter parameters from `GET /api/logs`.
- Exports up to 10,000 records by default.
- JSON returns the raw log array; CSV includes headers and field escaping.

---

## Database /api/admin/database

`src/main/java/com/xenoamess/damning_proxy/api/admin/DatabaseAdminApi.java:17`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/admin/database/backup?name=...` | Perform H2 hot backup to `~/.damning-proxy/backups/` |
| `POST` | `/api/admin/database/restore?path=...` | Validate and stage a restore file, return restart command |

### Backup

```bash
curl -X POST http://localhost:12360/api/admin/database/backup
```

Response:

```json
{
  "path": "/home/xxx/.damning-proxy/backups/backup_20260707_123456.zip",
  "success": true
}
```

- Default filename is `backup_YYYYMMDD_HHmmss.zip`.
- The `name` parameter can specify a filename but must not contain path separators.
- In-memory databases (`jdbc:h2:mem:`) do not support hot backup and will return 500.

### Restore

```bash
curl -X POST "http://localhost:12360/api/admin/database/restore?path=/home/xxx/.damning-proxy/backups/backup_20260707_123456.zip"
```

Response:

```json
{
  "stagedPath": "/home/xxx/.damning-proxy/data.restore.zip",
  "restartCommand": "Stop the application, then run: ..."
}
```

- Restore requires stopping the application, extracting `data.restore.zip` over the current database files, and then restarting.
- Online hot restore is not currently supported because the H2 file database is locked while the application is running.

---

## Metrics /api/metrics

`src/main/java/com/xenoamess/damning_proxy/api/admin/MetricsAdminApi.java:16`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/metrics/summary?startTime=&endTime=` | Summary metrics (total requests, errors, avg latency, tokens) |
| `GET` | `/api/metrics/time-series?startTime=&endTime=&bucketMinutes=` | Requests/errors/latency/tokens aggregated by time bucket |
| `GET` | `/api/metrics/top-instances?startTime=&endTime=&limit=` | Instances with the highest request counts |
| `GET` | `/api/metrics/status-distribution?startTime=&endTime=` | Success/error distribution |

- Time parameters format: `YYYY-MM-DDTHH:mm:ss`.
- Defaults to the last 24 hours when omitted.
- `bucketMinutes` defaults to 60 (hourly buckets); switches to daily buckets when spanning multiple days.

---

## Rate-Limit Settings /api/settings/rate-limit

`src/main/java/com/xenoamess/damning_proxy/api/admin/GlobalSettingsAdminApi.java:11`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/settings/rate-limit` | Get current rate-limit settings |
| `PUT` | `/api/settings/rate-limit` | Update rate-limit settings |

### Request/Response Body

```json
{
  "id": 1,
  "maxRequestsPerWindow": 100,
  "windowSeconds": 60,
  "createdAt": "2026-07-08T10:00:00",
  "updatedAt": "2026-07-08T10:00:00"
}
```

| Field | Type | Constraints | Description |
|---|---|---|---|
| `maxRequestsPerWindow` | Integer | Required, 1-1000000 | Max requests allowed in each window |
| `windowSeconds` | Integer | Required, 1-86400 | Rate-limit window in seconds |

Because global settings are cached, updates may take up to `damning-proxy.global-settings.cache-ttl-seconds` (default 60 s) to take effect.

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
