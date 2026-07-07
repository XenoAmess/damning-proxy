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
| P1-15 | Code quality | Split frontend chunks to reduce bundle size | `index.js` is close to 1 MB gzipped. Use route-based dynamic imports for Chat, Logs, and PluginEditor. |

---

## P2 — Backlog / On-Demand

| # | Category | Title | Description |
|---|----------|-------|-------------|
| P2-1 | Core proxy | Add `/audio/*` proxy endpoints | Support Whisper / TTS models to complete OpenAI protocol coverage. |
| P2-2 | Core proxy | Run response plugins per SSE chunk | Response plugins currently run only after the full stream is accumulated. Per-chunk modification would enable live filtering. |
| P2-3 | Plugin system | Groovy/JS plugin sandbox / allow-list | Scripts have full JVM access. Introduce a ClassShutter or security policy to restrict file/network access. |
| P2-4 | Plugin system | Plugin script revision history | Save script snapshots and support rollback. |
| P2-5 | Admin UI | Dashboard chart page | Visualize request, error, and token trends based on Micrometer metrics. |
| P2-6 | Admin UI | Filtered log export CSV/JSON | The logs page only supports pruning; add export of the current filtered view. |
| P2-7 | Operations | H2 hot backup / restore endpoint | Provide an admin API to trigger `BACKUP` and restore. |
| P2-8 | Operations | Rate-limit response headers | Return standard headers such as `RateLimit-Remaining` and `RateLimit-Reset`. |
| P2-9 | Code quality | Document `skip.frontend.build` property | Verify the property works and document it in the build guide. |
| P2-10 | Code quality | Native image reflection configuration | Plugin engines and script caches rely on reflection; add `reflect-config.json` and verify native build. |

---

## Suggested Execution Order

1. **P0 items**: Fix the real bugs first, then add the regression tests to lock in correct behavior.
2. **P1-1 (health check), P1-3 (DB indexes), P1-8/P1-9 (tests)**: Stabilize the core path.
3. **P1-11 (Docker), P1-12 (metrics), P1-7 (frontend tooling)**: Move toward production readiness.
4. **P2 items**: Pull in as business needs arise.
