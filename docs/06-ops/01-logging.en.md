[中文版](01-logging.md)

# 01 Traffic Log

> Last updated: 2026-06-17  
> Source version: current workspace

## What Is Logged

Every proxied request is recorded in the `TrafficLog` table:

- Request method, path, headers, body
- Response status code, headers, body
- Request/response timestamps and duration
- Plugin logs
- Plugin execution snapshots (friendly snapshots)

Entity definition: `src/main/java/com/xenoamess/damning_proxy/entity/TrafficLog.java`

**Note**: The `Authorization` header value is masked as `Bearer ***` to avoid writing the upstream token in plain text to the log.

---

## When Logs Are Recorded

`src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java`

| Step | Action |
|---|---|
| Request received | `TrafficLogService.recordRequest(...)` creates the log |
| After request-phase plugin execution | If returning directly, `recordResponse(...)` is called |
| After upstream response | `recordResponse(...)` updates the response info |
| Streaming response ends | `recordResponse(..., 200, ...)` is called asynchronously |

---

## Log Length Limits

`src/main/java/com/xenoamess/damning_proxy/service/TrafficLogService.java:27`

| Field | Max Length |
|---|---|
| requestHeaders / responseHeaders | 2000 |
| requestBody / responseBody | 10000 |
| pluginLogs | 5000 |
| friendlyPluginSnapshots | 8000 |

Values exceeding the limit are truncated and appended with `...[truncated]`.

---

## Querying Logs

### Admin API

```bash
# Latest 100 entries
curl http://localhost:12360/api/logs

# Filter by instance
curl "http://localhost:12360/api/logs?instanceId=1&limit=50"

# Filter by profile
curl "http://localhost:12360/api/logs?profileId=1&limit=50"

# Single log entry
curl http://localhost:12360/api/logs/1

# Friendly format
curl http://localhost:12360/api/logs/1/friendly
```

### Web Admin

Open the **Traffic Log** page:

- Card-style list showing recent logs.
- Click a card to view details.
- Details include: conversation summary, request plugin pipeline, response plugin pipeline, raw request/response, and plugin logs.
- Supports deleting a single log and clearing all logs.

---

## Log Cleanup

```bash
# Delete a single log
curl -X DELETE http://localhost:12360/api/logs/1

# Clear all logs
curl -X POST http://localhost:12360/api/logs/clear
```

---

## Friendly Log Fields

`src/main/java/com/xenoamess/damning_proxy/api/admin/LogAdminApi.java:77`

The friendly log extracts from the raw log:

- `userPrompt`: the user prompt
- `modelOutput`: the model output
- `model`: the requested model name
- `requestPipeline`: list of request-phase plugin snapshots
- `responsePipeline`: list of response-phase plugin snapshots

Each snapshot contains: plugin name, phase, input, output, whether it errored, and error message.

---

## Database Storage

Default H2 file database path:

```text
${user.home}/.damning-proxy/data.mv.db
```

To retain logs long-term, back up regularly or switch to PostgreSQL/MySQL.
