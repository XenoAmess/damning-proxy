[中文版](improvement-checklist.md)

# Project Optimization and Improvement Checklist

> Audit date: 2026-06-21  
> Completion date: 2026-06-21  
> Re-scan date: 2026-06-21  
> Scope: Backend Java (Quarkus) + Frontend Vue 3 admin-web

---

## Round 1 (Completed)

All 47 improvement items have been implemented, totaling 30 commits. See git log `d3e92ee..c33e91c`.

| Category | Count | Summary |
|----------|-------|---------|
| Thread Safety | 3 | `ConcurrentHashMap`, thread-safe `PluginContext`, heartbeat scheduler reuse |
| Bug Fixes | 2 | Removed SSL `setSsl(true)`, fixed `packagePath` timing during import |
| Performance | 8 | `HttpClient` reuse, `ScriptEngine` ThreadLocal, N+1 batch queries, lazy route loading, icon tree-shaking, localStorage debounce, deepCopy optimization, duplicate serialization elimination |
| Code Duplication | 6 | Backend template method + Validation + PanacheUtils, frontend shared utils |
| New Features | 14 | Script timeout, circuit breaker, rate limiter, log retention, pagination metadata, health DB check, streaming `tool_calls`, axios interceptors, 404 route, session trimming, save loading, AbortController, cancel/close detection |
| Code Quality | 9 | Externalized resource files, silent catch fixes, `ProfileForm` DTO, dead code/file removal, `lang="zh-CN"`, exclude `package-lock.json` |
| Accessibility | 3 | ARIA roles, keyboard navigation, focus trap |

Skipped: `#10` (worker thread pool is sufficient), `#34` (TypeScript migration risk is high), `#47` (already in `.gitignore`)

---

## Round 2 (Completed 14/20)

Total 10 commits, see git log `a9167d7..2065413`.

### High (4/4 ✓)

| # | Status | Description |
|---|--------|-------------|
| R1 | ✓ | Added `@PreDestroy shutdownNow()` to 3 `ExecutorService` instances |
| R2 | ✓ | `ThreadLocal` Nashorn engine `remove()` in `finally` block |
| R3 | ✓ | `ProfileUpdate` now checks for slug uniqueness conflicts |
| R4 | ✓ | Group export now filters out null plugins/scripts |

### Medium (6/6 ✓)

| # | Status | Description |
|---|--------|-------------|
| R5 | ✓ | Restored 4 missing icon imports |
| R6 | ✓ | Replaced `console.error` with `ElMessage.warning` |
| R7 | ✓ | Introduced `DOMPurify` to sanitize `marked.parse()` output |
| R8 | ✓ | Token/config moved to `sessionStorage` |
| R9 | ✓ | Added logging for SSE chunk parsing and file deletion |
| R10 | ✓ | Moved `ensureCompiled` into executor thread to avoid double compilation |

### Low (4/10 ✓, 6 items marked "Later")

| # | Status | Description |
|---|--------|-------------|
| R11 | Later | Remaining `HashMap` usage — functionally safe, unify on demand |
| R12 | ✓ | Removed unused `ArrayList` import from `PluginContext` |
| R13 | ✓ | Removed `@ApplicationScoped` from `ZipBuilder` and deleted unused methods |
| R14 | Later | JSON import loses `mode` — non-critical path |
| R15 | ✓ | Added try-catch error logging for executor lambdas |
| R16 | Later | `ExecutorService` CDI producer — managed by Quarkus by default |
| R17 | Later | `@Transactional` consistency — purely decorative |
| R18 | Later | `RateLimiter` weak-consistency comment — functionally safe |
| R19 | Later | Export-with-none-selected prompt — full export semantics already exist |
| R20 | ✓ | `stopStreaming` resets `typewriterBuffer`/`Target` |
