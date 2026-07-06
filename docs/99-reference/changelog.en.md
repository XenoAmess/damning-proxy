[中文版](changelog.md)

# Changelog

> Last updated: 2026-07-06  
> Source version: current workspace

## 2026-07-06

- `/v1/health` now includes a circuit breaker snapshot, making it easier to distinguish proxy issues from upstream failures.
- `ProxyProfile` now has configurable circuit breaker fields: failure threshold and open timeout, exposed in the admin UI.
- Added streaming proxy test `ProxyApiTest.shouldProxyStreamingChatCompletions`.
- Fixed streaming proxy logging: a normal `[DONE]` termination now records status 200 and the aggregated response body, instead of prematurely recording an empty body or 499.
- Markdown code blocks in Chat messages now have a hover "Copy" button.
- Admin Chat session history moved from localStorage to IndexedDB; legacy data is automatically imported on first load.
- Admin Chat page adds a parameter panel: configure temperature, top_p, max_tokens, and system prompt.
- Admin Chat messages now support "Resend" and "Regenerate" actions.
- Admin traffic logs support filtering by instance, status, path keyword, and time range, plus pagination.
- Added plugin debugger page with Groovy syntax highlighting, sample-request dry-run, and save-time script validation.
- Fixed `PanacheLogRepository` dynamic query error caused by an unused parameter when no filters were applied.

## 0.1.0 — 2026-06-21

First release. Main changes:

- Version changed from `1.0-SNAPSHOT` to `0.1.0`.
- Added upstream fault circuit breaker (`CircuitBreaker`) that automatically opens the circuit for continuously failing upstreams.
- Added proxy request rate limiter (`RateLimiter`) with per-instance request rate caps.
- Traffic logs now support automatic retention / auto-pruning; retention policies can be configured by days or by entry count.
- Health check (`HealthResource`) now includes database connectivity verification.
- Script execution now has a 30-second timeout to prevent Groovy/JavaScript plugins from running indefinitely.
- Streaming proxy response plugins now support SSE forwarding of `tool_calls`.
- Multiple thread-safety fixes: core structures switched to `ConcurrentHashMap`, `PluginContext` made thread-safe.
- Performance optimizations: reused `HttpClient` instances, `ScriptEngine` caching, N+1 query fixes, lazy route loading.
- Code deduplication and cleanup.
- Frontend accessibility improvements (ARIA attributes, keyboard navigation support).

## 2026-06-20

- Updated background-run documentation to recommend managing `mvn quarkus:dev` with `screen`, avoiding leftover processes that occupy ports or hold the H2 lock.
- Updated proxy endpoint documentation:
  - `/v1/proxy/{instanceSlug}/chat/completions` is now a single path that returns JSON or SSE based on the `stream` field.
  - Added implementation notes on bridging `Multi<String>` to SSE via `StreamingOutput`.
  - Added notes on request header/body merge priority, upstream timeout disabling strategy, and the 30-second heartbeat diagnostic.

## 2026-06-17

- Initialized the damning-proxy knowledge base.
- Added project overview, architecture design, data model, proxy flow, plugin system, API documentation, build/run/self-test guide, and operations documentation.
- Migrated original `doc/plan.md` to `docs/99-reference/plan.md`.
- Established knowledge-base evolution convention: code changes must be synchronized with the corresponding documentation updates.

---

## Historical Changes

(To be added in subsequent versions)
