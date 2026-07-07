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

| # | Category | Title | Problem and Approach |
|---|----------|-------|----------------------|
| P0-1 | Core proxy | Non-streaming upstream errors return HTTP 500 | `OpenAiProxyService.proxyRequest()` catches the upstream `WebApplicationException` (502/504) and wraps it in a `RuntimeException`, which `GlobalExceptionMapper` converts to 500. Re-throw the original `WebApplicationException` or construct a new one with the upstream status. |
| P0-2 | Core proxy | Profile circuit-breaker settings are dropped | The UI already supports `circuitBreakerFailureThreshold` and `circuitBreakerOpenTimeoutSeconds`, but the backend `ProfileForm` record and `ProfileAdminApi.toEntity()` do not include them, so the values are silently discarded. Add the fields to the DTO, conversion logic, and export. |
| P0-3 | Testing | Regression test for non-streaming upstream 502/504 | `ProxyApiTest` covers streaming errors but not the non-streaming 502/504 path. Use WireMock to simulate upstream failures and assert the proxy returns the correct status and logs the error. |

---

## P1 — Should Complete Soon

| # | Category | Title | Value and Approach |
|---|----------|-------|--------------------|
| P1-1 | Core proxy | Add real upstream connectivity to `/v1/health` | Current `/v1/health` only checks the database and circuit-breaker snapshot. Add a lightweight HTTP probe to each enabled profile's base URL or `/v1/models` and include per-upstream status in the response. |
| P1-2 | Core proxy | Add retry/backoff for transient upstream failures | Only the circuit breaker reacts to failures today. Add configurable retry count and exponential backoff for 5xx and connect timeouts to reduce false positives. |
| P1-3 | Core proxy | Add database indexes on `TrafficLog` query columns | `PanacheLogRepository` filters by `instanceId`, `profileId`, `requestPath`, `requestTime`, and `responseStatus`. Add `@Table` indexes to avoid degradation as the log table grows. |
| P1-4 | Plugin system | Enforce ZIP plugin package size limits and validate structure | Currently only path normalization is validated. Add a size limit and require a valid `main.groovy` / `main.js` entry. |
| P1-5 | Plugin system | Make plugin script timeout configurable | Groovy and JS engines hardcode 30 seconds. Expose `damning-proxy.plugin.timeout-ms`. |
| P1-6 | Admin UI | Add global rate-limiter configuration page | Rate limits are currently configured only via `application.properties`. Add a `/api/settings/rate-limit` endpoint and a management page. |
| P1-7 | Admin UI | Add ESLint / Prettier / type-check tooling | `admin-web/package.json` lacks lint, format, and type-check scripts. Add the toolchain and enforce it in CI. |
| P1-8 | Testing | Add unit tests for `RateLimiter`, `CircuitBreaker`, and `UpstreamHttpClient` | These core resilience components are only exercised indirectly. Add pure unit tests that simulate time and failure scenarios. |
| P1-9 | Testing | Add plugin-to-proxy end-to-end test | No test verifies that a request-phase plugin rewrites the upstream request or that a response-phase plugin rewrites the returned body. Use WireMock with a real plugin. |
| P1-10 | Documentation | Sync stale plugin cache note | `docs/05-guides/02-plugin-development.en.md` still states scripts require a restart after modification, but updates now invalidate the cache. |
| P1-11 | Operations | Provide Dockerfile and docker-compose | Docs only contain a manual snippet. Provide version-controlled `Dockerfile` and `docker-compose.yml` with a mounted H2 volume. |
| P1-12 | Operations | Add Prometheus / Micrometer metrics | Monitoring docs say metrics are not integrated. Add `quarkus-micrometer` and expose request, error, latency, token usage, and circuit-breaker metrics. |
| P1-13 | Code quality | Add age-based log retention | Retention is currently count-based. Add `damning-proxy.log.max-age-days` to prune by time window. |
| P1-14 | Code quality | Unify Admin API exception handling | `PluginAdminApi` and plugin engines still throw `RuntimeException` for IO/JSON errors, resulting in 500. Convert to `WebApplicationException` with explicit status codes. |
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
