[中文版](04-distributed-state.md)

# 04 Distributed-State Limitation

> Last updated: 2026-07-08
> Source version: current workspace

`damning-proxy` runs as a single process by default. State is divided into two categories:

- **Persistent state**: stored in the database (H2 file database by default) and accessed through JPA/Hibernate.
- **In-process state**: only exists in the memory of a single JVM instance and is not shared across processes.

---

## In-Process State List

| State | Location | Description |
|---|---|---|
| Circuit breaker state | `proxy/CircuitBreaker.java` | Each `ProxyProfile` maintains a `CircuitBreaker.State` that tracks consecutive failures and half-open/open states. |
| Rate limiter counter | `proxy/RateLimiter.java` | Memory-based sliding-window counter driven by `maxRequestsPerWindow` and `windowSeconds`. |
| Plugin compile cache | `plugin/engine/GroovyPluginEngine.java`<br>`plugin/engine/JavaScriptPluginEngine.java` | Compiled script artifacts (Groovy `Script` objects, JS `CompiledScript`) are cached in `ThreadLocal` or Guava Cache. |
| Global settings cache | `repository/panache/PanacheGlobalSettingsRepository.java` | Global settings are cached for a short TTL (default 60 s) to reduce H2 queries. |

---

## Impact of Horizontal Scaling

When running multiple `damning-proxy` instances (multiple containers/replicas):

1. **Circuit breakers are not shared**: if instance A trips a breaker for an upstream, instance B still sends requests to that upstream.
2. **Rate limits are not shared**: the total request volume is split across per-instance counters, so the effective threshold is higher than configured.
3. **Plugin caches are per-instance**: each instance compiles and caches scripts independently, so the first execution after startup still incurs compilation overhead.
4. **Global settings cache lag**: after updating `/api/settings/rate-limit`, instances will apply the change only after their cache expires, up to `damning-proxy.global-settings.cache-ttl-seconds` (default 60 s).

---

## Recommendations

- **Single-instance deployment**: the default and simplest mode; no extra work required.
- **Multiple instances + sticky sessions**: if the client can be pinned to the same instance, it can partially mitigate rate-limit and breaker inconsistency, but does not fully solve it.
- **Externalize state**: for strict multi-replica consistency, extend the implementation yourself:
  - Use Redis or another shared store for circuit breaker and rate-limit counters.
  - Use centralized caching or messaging to propagate global settings changes instantly.

> The current version does not include a Redis extension. If you need distributed deployment, externalize the above state as a future customization.
