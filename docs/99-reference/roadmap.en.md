[中文版](roadmap.md)

# Project Roadmap

> Last updated: 2026-07-06  
> Source version: current workspace

This document lists the next executable optimization and feature backlog for damning-proxy, based on the current codebase and the local-only usage scenario. Security hardening is not prioritized because the project is intended for local / trusted-network use.

---

## Phase 1: Plugin Development Experience (biggest daily impact)

| # | Item | Goal | Status |
|---|------|------|--------|
| 1 | Plugin changes take effect without restart | Fix the pain caused by Groovy/JS engine script caching | Done |
| 2 | Plugin dry-run / validation | Enter sample requests in the UI and see before/after diff without calling upstream | Done |
| 3 | Groovy syntax highlighting | Current Groovy scripts fall back to JS highlighting, which is poor | Done |
| 4 | Plugin errors surfaced more clearly | Errors currently only appear in friendly snapshots; saving gives no UI feedback | TODO |

---

## Phase 2: Admin Console UX

| # | Item | Goal | Status |
|---|------|------|--------|
| 5 | Log filtering / pagination / time range | Currently hardcoded limit=100; hard to browse large logs | Done |
| 6 | Chat page controls for temperature / top_p / max_tokens / system message | Currently only messages can be sent; parameters are missing | Done |
| 7 | Resend / regenerate in chat | Common LLM UI feature currently missing | Done |
| 8 | Move chat session history from localStorage to IndexedDB or optional persistence | localStorage is small and stores plaintext; IndexedDB is more robust locally | Done |
| 9 | One-click copy for markdown code blocks | Easier to copy code from logs / chat | Done |

---

## Phase 3: Stability & Observability

| # | Item | Goal |
|---|------|------|
| 10 | Streaming proxy tests | Streaming is the most failure-prone path and currently untested |
| 11 | Configurable circuit breaker | Different upstreams need different failure thresholds and recovery times |
| 12 | Upstream connectivity in health check | Distinguish proxy failure from upstream API failure |
| 13 | Bulk deletion for log pruning | Currently slow when logs approach 100k entries |
| 14 | Friendlier SSE error on upstream streaming failure | Client may currently see an abrupt stream termination |

---

## Phase 4: Feature Extensions (as needed)

| # | Item | Goal |
|---|------|------|
| 15 | Support embeddings endpoint | Most common next OpenAI endpoint for local vector models |
| 16 | Support images/generations | For image generation models |
| 17 | Token usage stats in traffic logs | See token cost per request |
| 18 | Import/export preview | More control when backing up / migrating configs |

---

## Phase 5: Code & Documentation Cleanup

| # | Item | Goal |
|---|------|------|
| 19 | Update outdated docs | Fix streaming 400 description, `tool_calls` status, changelog, etc. |
| 20 | Split Chat.vue / Logs.vue | Both are near 1000 lines; increasingly hard to maintain |
| 21 | Pay off checklist leftovers: HashMap cleanup, @Transactional, ExecutorService | Technical debt from improvement-checklist |

---

## Recommended Execution Order

1. Do 1–4 first (cache eviction, dry-run, Groovy highlighting, error surfacing) — immediate daily benefit for plugin authors.
2. Then 5–9 (log filtering, chat parameters, resend/regenerate, copy code) — improves daily usage.
3. Then 10–14 (streaming tests, circuit breaker config, health check, log cleanup) — long-term stability.
4. Finally do 15–18 feature extensions and 19–21 cleanup as needed.

---

## Notes

- This roadmap is for the local-only version. Production-grade security items (admin auth, CORS lockdown, token encryption) are intentionally excluded.
- Update this document when items are completed, and add relevant entries to the [changelog](changelog.en.md) if the change is significant.
