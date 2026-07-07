[中文版](02-monitoring.md)

# 02 Health Checks and Monitoring

> Last updated: 2026-07-07  
> Source version: current workspace

## Health Check Endpoint

`src/main/java/com/xenoamess/damning_proxy/api/HealthResource.java:11`

```bash
curl http://localhost:12360/v1/health
```

Expected response:

```json
{
  "status": "ok"|"degraded",
  "database": "ok"|"error",
  "upstreams": {
    "profile-slug": {
      "enabled": true,
      "status": "up"|"down",
      "statusCode": 200,
      "baseUrl": "https://api.openai.com/v1"
    }
  },
  "circuitBreakers": { ... }
}
```

Notes:

- `status` is `ok` only when the database connection is healthy **and** all enabled (`enabled=true`) profiles are reachable.
- For each enabled profile, the health check sends a 5-second timeout GET probe to `baseUrl + /models`.
- An HTTP status code < 500 is considered `up` (including 401/403/404, which indicate reachability); connection failures or 5xx responses are considered `down`.
- Disabled profiles show `enabled: false` and `status: "disabled"` and do not affect the overall status.
- `circuitBreakers` returns the current circuit-breaker snapshot per upstream.

---

## Prometheus Metrics

The project integrates `quarkus-micrometer` and `quarkus-micrometer-registry-prometheus`. Prometheus metrics are exposed by default at `/q/metrics`.

```bash
curl http://localhost:12360/q/metrics
```

### Key Metrics

| Metric Name | Type | Labels | Description |
|---|---|---|---|
| `damning_proxy_requests_total` | Counter | `instance`, `path`, `status` | Total proxy endpoint requests (by instance, path, and status code) |
| `damning_proxy_request_duration_seconds` | Timer | `instance`, `path` | Proxy endpoint request processing time |
| `damning_upstream_requests_total` | Counter | `baseUrl`, `status` | Total upstream HTTP requests (including final status code) |
| `damning_upstream_request_duration_seconds` | Timer | `baseUrl` | Upstream request duration (including retries) |
| `damning_tokens_total` | Counter | `instance`, `type` | Token usage (`type=prompt/completion/total`) |
| `damning_rate_limit_requests_total` | Counter | `instance`, `result` | Rate limit results (`acquired` / `rejected`) |
| `damning_circuit_breaker_state` | Gauge | `baseUrl` | Circuit breaker state (`0=closed`, `1=open`, `2=half_open`) |

Notes:

- Proxy request metrics are collected in `TrafficLogService.recordResponse()` and cover both non-streaming and streaming final responses.
- Upstream metrics are collected in `UpstreamHttpClient.send()` and reflect the final upstream call after retries.
- Token usage is extracted from the `usage` field in upstream responses and is only emitted when the upstream returns a standard OpenAI format.

### Configuration

`src/main/resources/application.properties`:

```properties
quarkus.micrometer.enabled=true
quarkus.micrometer.export.prometheus.enabled=true
```

Set `quarkus.micrometer.export.prometheus.enabled=false` to disable Prometheus export.

---

## Metrics Available for Monitoring

The current version integrates Micrometer/Prometheus:

### 1. Health Checks

Periodically request `/v1/health`. HTTP 200 means the service is alive.

### 2. Prometheus Metrics

Scrape `/q/metrics` for QPS, latency, error rate, token usage, and circuit-breaker state.

### 3. Logs

Check application logs for errors and upstream request failures.

```text
ERROR [com.xenoamess.damning_proxy.filter.GlobalExceptionMapper] Unhandled exception
ERROR [com.xenoamess.damning_proxy.proxy.UpstreamHttpClient] Upstream request failed
```

### 4. Traffic Logs

Analyze request volume, error rate, and average latency via `/api/logs`.

---

## Adjusting Log Levels

`src/main/resources/application.properties:19`

```properties
quarkus.log.level=INFO
quarkus.log.category."com.xenoamess.damning_proxy".level=DEBUG
```

For more detailed upstream request logs, DEBUG is already enabled for the project package, printing:

```text
DEBUG [com.xenoamess.damning_proxy.proxy.UpstreamHttpClient] Upstream request: POST https://api.openai.com/v1/chat/completions
```

---

## Implemented Features

### CircuitBreaker

`src/main/java/com/xenoamess/damning_proxy/proxy/CircuitBreaker.java`

Independent circuit breaker per upstream baseUrl:
- 3 consecutive failures → breaker opens and rejects subsequent requests
- After 30 seconds open → half-open, allowing one probe request
- Probe succeeds → breaker closes; probe fails → breaker opens again

### RateLimiter

`src/main/java/com/xenoamess/damning_proxy/proxy/RateLimiter.java`

Sliding-window rate limit counted by instance slug:
- Default max 60 requests per instance per 60-second window
- Adjust via `damning-proxy.rate-limit.max-requests` and `damning-proxy.rate-limit.window-seconds`
- Requests exceeding the limit return HTTP 429

### Log Retention Policy

`src/main/java/com/xenoamess/damning_proxy/service/TrafficLogService.java`

- Checks total count after every 100 writes
- Automatically deletes oldest logs when exceeding `damning-proxy.log.max-count` (default 100000)
- Threshold can be adjusted via configuration

