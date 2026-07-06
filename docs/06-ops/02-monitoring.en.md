[中文版](02-monitoring.md)

# 02 Health Checks and Monitoring

> Last updated: 2026-06-21  
> Source version: current workspace

## Health Check Endpoint

`src/main/java/com/xenoamess/damning_proxy/api/HealthResource.java:11`

```bash
curl http://localhost:12360/v1/health
```

Expected response:

```json
{ "status": "ok"|"degraded", "database": "ok"|"error" }
```

---

## Metrics Available for Monitoring

The current version does not integrate Micrometer/Prometheus. Monitoring can be done as follows:

### 1. Health Checks

Periodically request `/v1/health`. HTTP 200 means the service is alive.

### 2. Logs

Check application logs for errors and upstream request failures.

```text
ERROR [com.xenoamess.damning_proxy.filter.GlobalExceptionMapper] Unhandled exception
ERROR [com.xenoamess.damning_proxy.proxy.UpstreamHttpClient] Upstream request failed
```

### 3. Traffic Logs

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

---

## Future Improvements

- Integrate `quarkus-micrometer` to expose Prometheus metrics.
- Track QPS, latency percentiles, and upstream error rates.
