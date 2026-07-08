[中文版](changelog.md)

# Changelog

> Last updated: 2026-07-08
> Source version: current workspace

## 2026-07-08

- Explicitly dropped **GraalVM Native Image** support: the dynamic Groovy / JavaScript script engines conflict with the native-image closed-world assumption, so the related configuration and plugins have been removed.
- Performance improvements:
  - `GlobalSettings` is now cached in memory; `RateLimiter` no longer queries H2 on every request.
  - `MetricsService.timeSeries()` was rewritten to use JPQL + Java-side bucketing, making it portable across databases (PostgreSQL/MySQL).
  - `TrafficLog` cleanup is now batched, avoiding performance issues with large log tables.
- Correctness fixes:
  - The circuit breaker now records upstream responses as success only for 2xx status codes; 4xx/5xx are counted as failures.
  - Removed `@CreationTimestamp` from `TrafficLog.requestTime` so manually set timestamps are not overwritten.
- Plugin system improvements:
  - Plugin cache keys now use SHA-256 instead of `String.hashCode()` to avoid collisions.
  - The plugin execution thread pool is now a bounded `ThreadPoolExecutor` (configurable via `damning-proxy.plugin.execution.pool-size`), preventing unbounded thread growth.
  - Plugin uploads and ZIP imports now enforce size and entry-count limits (`BoundedInputStream`).
- Code quality:
  - `slug` values are now strictly validated with `^[a-zA-Z0-9_-]+$` across profiles, instances, plugins, groups, and imports.
  - `StartupMigration` initializes sample plugins/groups only once and no longer overwrites existing data.
- Documentation:
  - Added "Distributed-State Limitation" operations doc explaining that circuit breaker, rate limiter, plugin cache, and global settings cache are in-process only and not shared across replicas.
  - Completed Admin API docs: added `/api/settings/rate-limit`, plugin dry-run, template, revisions, rollback, entries, and ZIP import endpoints.
  - Updated Dockerfile with a `mvn dependency:go-offline` layer to reuse Maven dependency cache.

## 2026-07-07

- Added tests covering plugin save validation (400), plugin dry-run, and batched log pruning.
- Fixed `PanacheLogRepository.deleteOldest` not flushing between batches, which could cause subsequent batches in the same transaction to re-read already-deleted records.
- Plugin save errors are now surfaced in the UI: the validation error returned by the backend 400 response is displayed when saving from both the Plugins page and the Plugin Debugger, instead of only appearing in the friendly snapshot.
- Technical-debt cleanup: replaced remaining `HashMap` usage in the core proxy, health check, instance export, and log serialization modules with `LinkedHashMap`/`ConcurrentHashMap` for deterministic ordering and consistency with the project's thread-safety practices; verified that `@Transactional` and `ExecutorService` lifecycles are already properly managed.
- Split admin Chat / Logs pages: `Chat.vue` and `Logs.vue` are now decomposed into multiple reusable child components, reducing page complexity and maintenance burden.
- Added component directories `admin-web/src/components/chat/` and `admin-web/src/components/logs/`.
- Refreshed outdated project documentation: architecture, proxy flow, API docs, logging, and troubleshooting docs are updated and kept in sync across Chinese and English versions.
- Import/export now shows a preview before applying: added `ImportPreviewDialog` component; JSON imports for profiles, instances, plugin groups, and plugins display a preview of new vs. overwritten entries before confirming.
- Traffic logs now capture token usage: prompt_tokens / completion_tokens / total_tokens are automatically extracted from the upstream `usage` field and shown in the log detail and card.
- Added unit test `TrafficLogServiceTest.shouldExtractTokenUsageFromResponse`.
- Added proxy endpoints: `/v1/proxy/{instanceSlug}/embeddings` and `/v1/proxy/{instanceSlug}/images/generations`, compatible with OpenAI embeddings and images/generations APIs.
- Added corresponding proxy tests `ProxyApiTest.shouldProxyEmbeddings` and `ProxyApiTest.shouldProxyImageGenerations`.
- Streaming upstream failures now return a friendly SSE error: the backend emits an `event: error` event for connection failures, HTTP error status codes, etc.; the frontend parses it and surfaces the message instead of silently terminating the stream.
- Added streaming proxy error test `ProxyApiTest.shouldReturnSseErrorOnUpstreamStreamingFailure`.
- Log pruning is now batched: added backend `/api/logs/prune` endpoint supporting "keep last N" or "delete all"; admin traffic log page adds a "Bulk Cleanup" button with confirmation dialog.
- Fixed auto-prune performance issue when the log table is large: `PanacheLogRepository.deleteOldest` now deletes in default 1000-row batches.

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
