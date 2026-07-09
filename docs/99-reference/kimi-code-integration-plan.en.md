[中文版](kimi-code-integration-plan.md)

# Kimi-code Integration Plan

> Last updated: 2026-07-10  
> Source version: current workspace (no tag)  
> Status: completed

## Implementation Summary

- Added `admin-web/src/utils/kimi.js`: provides `generateKimiDeviceId()` and `buildKimiHeaders()` to generate the 7 special Kimi-code headers.
- Added a "Provider" dropdown to `admin-web/src/views/Profiles.vue`. Selecting `Kimi-code` auto-fills `baseUrl`, `defaultModel`, and `customHeaders`.
- Device ID is visible and editable in the form, and is persisted to backend `ProxyProfile` via `customHeaders`.
- The backend remains a generic OpenAI proxy with no changes.
- Added `admin-web/src/utils/kimi.test.js` and `admin-web/src/views/Profiles.test.js`.

## Verification

- Frontend tests: all `pnpm test` passed.
- Build verification: `pnpm build` succeeded.
- Code quality: `pnpm lint`, `pnpm format:check`, and `pnpm type-check` all passed.

---

## Background

Kimi-code (`kimi-for-coding`) exposes both Anthropic Messages and OpenAI-compatible endpoints. The community reference implementation [`opencode-kimi-full`](https://github.com/lemon07r/opencode-kimi-full) shows that when using the OpenAI-compatible endpoint, a set of `kimi-cli`-like fingerprint headers must be sent; otherwise the upstream may reject requests.

The goal of this plan is to let damning-proxy admins connect Kimi-code through a one-click admin-web preset, while keeping the backend a generic OpenAI-compatible proxy and emitting the same OpenAI protocol as other upstreams.

---

## Goals

1. Provide a `Kimi-code` preset in the admin "Upstream Configuration / Profile" page.
2. Auto-fill the endpoint, default model, and special headers when the preset is selected.
3. Make the Device ID visible and editable in the form, and persist it in the backend via `customHeaders`.
4. Let dynamic request-body fields (`prompt_cache_key`, `thinking`, `reasoning_effort`) be supplied by the client; the backend only forwards them.

---

## Constraints

- Do not implement or discuss any security-related items.
- Keep the backend a generic OpenAI proxy: **no new entity fields, no new provider type, no Java code changes**.
- Do not enforce frontend coverage thresholds.
- Keep CI lint non-blocking (`continue-on-error: true`).
- Synchronize documentation changes between Chinese and English versions, and update the index.

---

## Key Decisions

| Decision | Choice | Reason |
|---|---|---|
| Upstream protocol | OpenAI-compatible endpoint | Fully compatible with damning-proxy's existing proxy path; no protocol conversion needed. |
| Backend changes | None | Store special headers in `ProxyProfile.customHeaders`; reuse the existing `/chat/completions` path. |
| Device ID | Frontend generates → form input → persisted in backend `customHeaders` | Browsers cannot read `~/.kimi/device_id`, so generate a hyphen-less UUID for the user, who can replace it with their real ID. |
| Dynamic body fields | Client supplies per request | These fields are session/request-specific and should not be hard-coded in a Profile. |

---

## Kimi-code Special Headers

From `opencode-kimi-full` (`src/headers.ts` and `src/constants.ts`):

| Header | Value | Notes |
|---|---|---|
| `User-Agent` | `KimiCLI/1.41.0` | Must keep this prefix; otherwise the upstream may return 403. |
| `X-Msh-Platform` | `kimi_cli` | Platform identifier. |
| `X-Msh-Version` | `1.41.0` | CLI version. |
| `X-Msh-Device-Name` | Hostname (ASCII only) | e.g. `my-pc`. |
| `X-Msh-Device-Model` | Device model string | e.g. `Linux 6.1 x86_64`. |
| `X-Msh-Device-Id` | UUIDv4 with hyphens removed | Shared with `kimi-cli`'s `~/.kimi/device_id`. |
| `X-Msh-Os-Version` | OS version string | e.g. `Linux 6.1`. |

`Authorization: Bearer <token>` is still provided by `ProxyProfile.bearerToken`; the user must prepare their own Kimi token and paste it in.

---

## What Is `prompt_cache_key`?

`prompt_cache_key` is an optional top-level request-body field used by Kimi-code for **session-scoped prompt-cache reuse**. The client typically sends its session ID as the value, allowing the upstream to reuse cached prompt prefixes across multiple turns in the same session, reducing latency and cost.

In this plan, the field **is not** hard-coded in `Profile.customBody`; clients calling damning-proxy should include it in each request body, and the generic backend will forward it unchanged.

---

## Implementation Steps

1. **Frontend utilities**
   - Add `admin-web/src/utils/kimi.js` (or `.ts`).
   - Provide `generateKimiDeviceId()` to produce a hyphen-less UUIDv4.
   - Provide `buildKimiHeaders(deviceId)` returning the 7 special headers.

2. **Add Kimi-code preset in Profiles.vue**
   - Add a "Provider" dropdown in the upstream profile form with options:
     - `Generic OpenAI-compatible` (default, no preset)
     - `Kimi-code`
   - When `Kimi-code` is selected:
     - Fill `baseUrl` = `https://api.kimi.com/coding/v1`
     - Fill `defaultModel` = `kimi-for-coding`
     - Generate and show a Device ID input (editable)
     - Generate the remaining `X-Msh-*` headers from the browser environment and Device ID, and write them into the `customHeaders` JSON editor
   - Users can still override `customHeaders`, `bearerToken`, etc.

3. **Device ID management**
   - Add a visible Device ID input bound to `customHeaders["X-Msh-Device-Id"]` in the Kimi-code preset section.
   - When switching to the preset, generate a new value only if the current `customHeaders` does not already contain the header.
   - On save, the Device ID is persisted in the backend database as part of `customHeaders`.

4. **Dynamic field forwarding**
   - No backend changes are needed. If the client request body includes `prompt_cache_key`, `thinking`, or `reasoning_effort`, the generic proxy will forward them unchanged.

5. **Tests**
   - Add `admin-web/src/utils/__tests__/kimi.spec.js` to verify UUID format, header structure, and ASCII filtering.
   - Extend `Profiles.vue` tests to verify that selecting the Kimi-code preset fills baseUrl, model, and customHeaders correctly.

6. **Documentation**
   - Add this document (`docs/99-reference/kimi-code-integration-plan.md` and `.en.md`).
   - After implementation, optionally add Kimi-code configuration examples to `docs/04-api/01-proxy-api.md` and `docs/05-guides/03-connect-opencode.md`.

---

## Test Plan

| Test | Scope | Verification Points |
|---|---|---|
| `kimi.spec.js` | Utility functions | `generateKimiDeviceId()` returns a 32-char hex string; `buildKimiHeaders()` includes all 7 required headers and filters non-ASCII characters. |
| `Profiles.vue` tests | Frontend view | Selecting Kimi-code preset auto-fills baseUrl, defaultModel, and customHeaders; editing Device ID updates customHeaders. |
| Manual smoke test | End-to-end | Create a Kimi-code Profile and Instance, call `/v1/proxy/{slug}/chat/completions`, and confirm the upstream receives the correct special headers. |

---

## Risks and Follow-up

- **Risk**: The browser-generated `X-Msh-Device-Id` may differ from the real `kimi-cli` `~/.kimi/device_id`, but the user can manually override it. Other fingerprint fields (device model, OS version) are also best-effort approximations based on the browser environment.
- **Follow-up**: If future requirements include model discovery for multiple Kimi models, OAuth device-flow login, or Anthropic Messages protocol conversion, consider introducing a provider type or dedicated upstream handler in the backend.
