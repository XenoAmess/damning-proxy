[中文版](improvement-plan.md)

# Project Optimization and Action Plan

> Audit date: 2026-07-07  
> Scope: Backend Java (Quarkus) + Frontend Vue 3 admin-web  
> Status: Dynamically maintained; update progress as items are completed

---

## Introduction

This plan is derived from a review of the current codebase, documentation, recent commits, and runtime behavior. Items are prioritized into three tiers: **must fix immediately**, **should complete soon**, and **backlog / on-demand**. Each item is kept small, committable, and verifiable so it can be split into independent issues, PRs, or sprint tasks.

---

## P0 — Must Fix Immediately (Runtime bugs / data correctness)

| # | Status | Category | Title | Problem and Approach |
|---|--------|----------|-------|----------------------|
| P0-1 | ✓ 2026-07-07 | Core proxy | Non-streaming upstream errors return HTTP 500 | `OpenAiProxyService.proxyRequest()` caught the upstream `WebApplicationException` (502/504) and wrapped it in a `RuntimeException`, which `GlobalExceptionMapper` converted to 500. Fixed to throw `WebApplicationException` with the original status. |
| P0-2 | ✓ 2026-07-07 | Core proxy | Profile circuit-breaker settings are dropped | The UI already supported `circuitBreakerFailureThreshold` and `circuitBreakerOpenTimeoutSeconds`, but `ProfileForm` and `PanacheProfileRepository.save()` did not include them. Added the fields to the DTO, export/import, and repository field copy. |
| P0-3 | ✓ 2026-07-07 | Testing | Regression test for non-streaming upstream 502/504 | Added `shouldReturn502WhenUpstreamConnectionRefused` and `shouldReturn504WhenUpstreamTimesOut` to `ProxyApiTest`; added `shouldPersistCircuitBreakerFieldsOnProfileUpdate` to `AdminApiTest`. |

---

## P1 — Should Complete Soon

| # | Status | Category | Title | Value and Approach |
|---|--------|----------|-------|--------------------|
| P1-1 | ✓ 2026-07-07 | Core proxy | Add real upstream connectivity to `/v1/health` | Previously `/v1/health` only checked the database and circuit-breaker snapshot. Added a 5-second timeout GET probe to `baseUrl + /models` for each enabled profile, returning `up`/`down`/`disabled` per upstream. Overall `status` is `ok` only when DB and all enabled upstreams are healthy. |
| P1-2 | ✓ 2026-07-07 | Core proxy | Add retry/backoff for transient upstream failures | Previously only the circuit breaker reacted to failures. Added configurable retry count and exponential backoff (`damning-proxy.upstream.max-retries`, `retry-base-delay-ms`, `retry-max-delay-ms`, `retry-status-codes`, `retry-on-timeout`). Applies to both non-streaming and streaming upstream requests. |
| P1-3 | ✓ 2026-07-07 | Core proxy | Add database indexes on `TrafficLog` query columns | `PanacheLogRepository` filters by `instanceId`, `profileId`, `requestPath`, `requestTime`, and `responseStatus`. Added `@Table` indexes on these five columns to avoid degradation as the log table grows. |
| P1-4 | ✓ 2026-07-07 | Plugin system | Enforce ZIP plugin package size limits and validate structure | Previously only path normalization was validated. Added `damning-proxy.plugin.zip.max-size-bytes`, `max-entries`, and `max-entry-size-bytes` configs. `PluginPackageStorage.storePackage()` now validates total size, entry count, per-entry size, path traversal, and required `main.groovy`/`main.js` entry before persisting. `PluginAdminApi` catches validation failures and returns 400. |
| P1-5 | ✓ 2026-07-07 | Plugin system | Make plugin script timeout configurable | Groovy and JS engines hardcoded 30 seconds. Added `damning-proxy.plugin.timeout-ms` config; both engines now use the configured value with a 30-second default, plus timeout tests. |
| P1-6 | ✓ 2026-07-07 | Admin UI | Add global rate-limiter configuration page | Rate limits were only configurable via `application.properties`. Added `GlobalSettings` table, `/api/settings/rate-limit` read/write endpoints, and a "System Settings" admin-web page that takes effect immediately. |
| P1-7 | ✓ 2026-07-07 | Admin UI | Add ESLint / Prettier / type-check tooling | `admin-web/package.json` lacked lint, format, and type-check scripts. Added ESLint 9 with `eslint-plugin-vue` and `eslint-config-prettier`, Prettier, and `vue-tsc` + `tsconfig.json`; formatted all source files and fixed real issues such as prop mutation; CI now runs lint, type-check, and format-check. |
| P1-8 | ✓ 2026-07-07 | Testing | Add unit tests for `RateLimiter`, `CircuitBreaker`, and `UpstreamHttpClient` | These core resilience components are only exercised indirectly. Added pure unit tests that simulate time and failure scenarios. |
| P1-9 | ✓ 2026-07-07 | Testing | Add plugin-to-proxy end-to-end test | No test verifies that a request-phase plugin rewrites the upstream request or that a response-phase plugin rewrites the returned body. Used WireMock with a real plugin for end-to-end verification. |
| P1-10 | ✓ 2026-07-07 | Documentation | Sync stale plugin cache note | `docs/05-guides/02-plugin-development.en.md` still stated scripts require a restart after modification. Plugin scripts are actually cached by content hash, and `PluginAdminApi` calls `evictCache` on update; updated both language versions to clarify changes take effect on the next request. |
| P1-11 | ✓ 2026-07-07 | Operations | Provide Dockerfile and docker-compose | Docs only contained a manual Docker snippet. Added a multi-stage `Dockerfile` (Maven + pnpm build frontend, Eclipse Temurin 21 JRE runtime) and `docker-compose.yml` with an H2 data volume mounted at `/data`; run documentation updated in both languages. |
| P1-12 | ✓ 2026-07-07 | Operations | Add Prometheus / Micrometer metrics | Added `quarkus-micrometer` exposing request, error, latency, token usage, and circuit-breaker metrics. `/q/metrics` provides Prometheus-formatted metrics; monitoring docs updated in both languages. |
| P1-13 | ✓ 2026-07-07 | Code quality | Add age-based log retention | Retention is currently count-based. Added `damning-proxy.log.max-age-days` config (default 30 days); `pruneOldLogs` now runs age-based cleanup after count-based pruning. |
| P1-14 | ✓ 2026-07-07 | Code quality | Unify Admin API exception handling | `PluginAdminApi` and plugin engines still threw `RuntimeException` for IO/JSON errors, resulting in 500. Replaced all with `WebApplicationException` or direct `Response.status(500)` with clear messages; engine `RuntimeException` instances now include cause messages. |
| P1-15 | ✓ 2026-07-07 | Code quality | Split frontend chunks to reduce bundle size | `index.js` was ~1 MB. Configured `manualChunks` to split Element Plus (948K), shared deps (422K), and CodeMirror (402K) into independent vendor chunks; main entry reduced to 6 KB with CodeMirror lazy-loaded on demand. |

---

## P2 — Backlog / On-Demand

| # | Status | Category | Title | Description |
|---|--------|----------|-------|-------------|
| P2-1 | ✓ 2026-07-07 | Core proxy | Add `/audio/*` proxy endpoints | Support Whisper / TTS models to complete OpenAI protocol coverage. Added `POST /v1/proxy/{instance}/audio/speech` (JSON input, binary audio output), `/audio/transcriptions`, and `/audio/translations` (multipart file upload); `UpstreamHttpClient` gained `sendBinary()` and `sendMultipart()` methods. |
| P2-2 | ✓ 2026-07-07 | Core proxy | Run response plugins per SSE chunk | Response plugins previously ran only after the full stream was accumulated. Added `STREAM_CHUNK` execution phase; `OpenAiProxyService` now runs `STREAM_CHUNK` plugins on each SSE chunk of streaming chat completions, supporting real-time modification or filtering. |
| P2-3 | ✓ 2026-07-07 | Plugin system | Groovy/JS plugin sandbox / allow-list | Scripts had full JVM access. Added `PluginSandbox` enabled by default; Groovy uses `SecureASTCustomizer` to block forbidden imports and receivers at compile time, and JavaScript uses the Nashorn `ClassFilter` to prevent access to blocked Java classes. The deny list and on/off switch are configurable via `application.properties`. |
| P2-4 | ✓ 2026-07-07 | Plugin system | Plugin script revision history | Save script snapshots and support viewing / rollback. Added `PluginScriptRevision` entity and `GET /api/plugins/{id}/revisions` and `POST /api/plugins/{id}/revisions/{revisionId}/rollback` endpoints; admin-web plugin editor gained a Revision History panel with preview and rollback. |
| P2-5 | ✓ 2026-07-07 | Admin UI | Dashboard chart page | Visualize request, error, and token trends based on backend aggregated log metrics. Added `GET /api/metrics/{summary,time-series,top-instances,status-distribution}` and admin-web `Dashboard.vue` using ECharts. |
| P2-6 | ✓ 2026-07-07 | Admin UI | Filtered log export CSV/JSON | The logs page only supported pruning. Added `GET /api/logs/export?format=json|csv` with all filter parameters, up to 10,000 records; CSV includes headers and proper escaping. |
| P2-7 | ✓ 2026-07-07 | Operations | H2 hot backup / restore endpoint | Provide an admin API to trigger `BACKUP` and restore. Added `POST /api/admin/database/backup` (H2 hot backup to `~/.damning-proxy/backups/`) and `POST /api/admin/database/restore` (validate and stage restore file; requires restart due to H2 file lock). |
| P2-8 | ✓ 2026-07-07 | Operations | Rate-limit response headers | Return standard headers such as `RateLimit-Remaining` and `RateLimit-Reset`. Added `RateLimitInfo` record and `getRateLimitInfo()` method; all proxy endpoint responses now carry `RateLimit-Limit`, `RateLimit-Remaining`, and `RateLimit-Reset` headers. |
| P2-9 | ✓ 2026-07-07 | Code quality | Document `skip.frontend.build` property | Verified the property works at `pom.xml:186`; corrected the skip-frontend command in the build guide to `-Dskip.frontend.build=true`. |
| P2-10 | ✗ Removed 2026-07-08 | Code quality | Native image reflection configuration | ~~Plugin engines and script caches rely on reflection; add `reflect-config.json` and verify native build.~~ **GraalVM Native Image is explicitly not supported**: the Groovy / JavaScript dynamic script engines conflict with native-image's closed-world assumption and cannot run reliably, so the related changes have been reverted and the documentation now states that native image is unsupported. |

---

## Suggested Execution Order

1. **P0 items**: Fix the real bugs first, then add the regression tests to lock in correct behavior.
2. **P1-1 (health check), P1-3 (DB indexes), P1-8/P1-9 (tests)**: Stabilize the core path.
3. **P1-11 (Docker), P1-12 (metrics), P1-7 (frontend tooling)**: Move toward production readiness.
4. **P2 items**: Pull in as business needs arise.

---

## Next Phase Plan

> Based on the 2026-07-08 code audit, focused on **non-security** performance, correctness, maintainability, documentation, and engineering efficiency.

### N1 — Performance / Correctness / Maintainability

| # | Status | Category | Title | Description |
|---|---|----------|-------|-------------|
| N1-1 | ✓ | Core proxy | Cache GlobalSettings in RateLimiter | `tryAcquire` currently queries H2 on every request. Cache `GlobalSettings` in memory and refresh on admin updates. |
| N1-2 | ✓ | Core proxy | Make metrics SQL dialect-neutral | Currently uses H2-only `FORMATDATETIME`. Rewrite with JPQL/HQL or dialect-neutral functions to support PostgreSQL/MySQL. |
| N1-3 | ✓ | Code quality | Bulk-delete aged logs | `deleteOlderThan` currently calls `listAll()` and iterates. Use `DELETE FROM TrafficLog WHERE requestTime < :cutoff`. |
| N1-4 | ✓ | Code quality | Enforce strict slug validation | Currently only rejects blank slugs. Standardize on `^[a-zA-Z0-9_-]+$` for profiles, instances, plugins, and groups. |
| N1-5 | ✓ | Core proxy | Circuit breaker should only treat 2xx as success | Currently any exception-free response counts as success. Record success only for 2xx responses. |
| N1-6 | ✓ | Plugin system | Use SHA-256 for plugin cache keys | Currently uses `String.hashCode()`, which can collide. Switch to SHA-256 of script content. |
| N1-7 | ✓ | Plugin system | Bound plugin execution thread pool | Currently uses `newCachedThreadPool()`. Replace with a bounded executor or Quarkus ManagedExecutor. |
| N1-8 | ✓ | Plugin system | Stream and limit upload/import sizes | Multipart and ZIP files are read fully into memory. Add max file size, max entry size, and streaming bounds. |
| N1-9 | ✓ | Code quality | Do not overwrite sample plugins on startup | `StartupMigration` currently overwrites sample scripts on every restart. Initialize sample data only once. |
| N1-10 | ✓ | Core proxy | Forward upstream error body | After retries are exhausted, the upstream body is discarded. Include it in the `WebApplicationException` response. |

### N2 — Documentation / Engineering Efficiency / Backlog

| # | Status | Category | Title | Description |
|---|---|----------|-------|-------------|
| N2-1 | — | Documentation | Audit docs against code | README/data model/flow diagrams are missing `GlobalSettings`, `PluginScriptRevision`, audio endpoints, metrics, etc. Admin API docs completed. |
| N2-2 | — | Admin UI | Add admin-web frontend tests | Introduce Vitest + Vue Test Utils for critical views. |
| N2-3 | ✓ | Code quality | Standardize frontend lockfile | Both `package-lock.json` and `pnpm-lock.yaml` existed. Standardized on pnpm, removed `package-lock.json`, and switched Maven `frontend-maven-plugin` to pnpm. |
| N2-4 | ✓ | Plugin system | Reuse ThreadLocal engine cache in JS engine | Currently creates a new Nashorn engine per execution. Reuse the ThreadLocal cache. |
| N2-5 | ✓ | Operations | Document distributed-state limitation | Circuit breaker / rate limiter state is in-memory only; document multi-replica limitation or provide optional Redis extension. |
| N2-6 | ✓ | Operations | Cache Maven dependencies in Dockerfile | Add a `mvn dependency:go-offline` layer to avoid re-downloading dependencies on every build. |
| N2-7 | ✓ | Documentation | Complete Admin API docs | Missing revisions, settings, plugin entries, rate-limit headers, etc. |

### Suggested Execution Order

1. **N1-1 / N1-2 / N1-3**: Small changes with large performance and portability gains.
2. **N1-5 / N1-6 / N1-9**: Correctness fixes to avoid false positives.
3. **N1-4 / N1-7 / N1-8**: Boundaries and stability.
4. **N2 documentation and engineering efficiency**.

