[中文版](03-error-handling.md)

# 03 Error Handling

> Last updated: 2026-06-17  
> Source version: current workspace

## Global Exception Handling

`src/main/java/com/xenoamess/damning_proxy/filter/GlobalExceptionMapper.java:13`

All uncaught exceptions are handled uniformly by `GlobalExceptionMapper`.

---

## Error Response Format

### 400 Bad Request

OpenAI-compatible style:

```json
{
  "error": {
    "message": "Example parameter error",
    "type": "invalid_request_error"
  }
}
```

Trigger scenarios:

- Admin API parameters are missing (in this case plain text may be returned, e.g., `slug is required`).
- Streaming endpoints now **unify** `stream=true` handling and no longer return this 400.

---

### 500 Internal Server Error

```json
{
  "error": {
    "message": "Internal server error: ...",
    "type": "internal_error"
  }
}
```

Trigger scenarios:

- Uncaught runtime exceptions.
- Severe upstream request failures, etc.

---

## Proxy Endpoint Status Codes

| Status Code | Trigger Scenario |
|---|---|
| 200 | Proxy success |
| 400 | Request parameter error (admin API) |
| 403 | Instance or Profile is disabled |
| 404 | Instance / Profile / PluginGroup does not exist |
| 429 | Rate limit exceeded |
| 502 | Upstream request failed (`UpstreamHttpClient` throws `WebApplicationException(BAD_GATEWAY)`) |
| 500 | Uncaught exception |
| 503 | Upstream circuit breaker open; service temporarily unavailable |

---

## Admin API Status Codes

| Status Code | Trigger Scenario |
|---|---|
| 200 | GET/PUT success |
| 201 | POST creation success |
| 204 | DELETE success |
| 400 | Parameter error (missing slug, profileId does not exist, etc.) |
| 404 | Resource not found |
| 409 | slug conflict |

---

## Errors in Logs

`GlobalExceptionMapper` prints all uncaught exceptions to the log:

```text
ERROR [com.xenoamess.damning_proxy.filter.GlobalExceptionMapper] Unhandled exception
```

When encountering a 500 error, check the server logs first to locate the cause.
