[English Version](kimi-code-integration-plan.en.md)

# Kimi-code 接入计划

> 最后更新：2026-07-10  
> 对应源码版本：当前工作区（未打 tag）  
> 状态：已完成

## 实现概要

- 新增 `admin-web/src/utils/kimi.js`：提供 `generateKimiDeviceId()` 与 `buildKimiHeaders()`，生成 Kimi-code 所需的 7 个特殊请求头。
- 在 `admin-web/src/views/Profiles.vue` 增加「提供商」下拉框，选择 `Kimi-code` 后自动填充 `baseUrl`、`defaultModel` 与 `customHeaders`。
- Device ID 在表单中可见、可编辑，最终随 `customHeaders` 保存到后端 `ProxyProfile`。
- 后端保持通用 OpenAI 代理，未做任何改动。
- 新增 `admin-web/src/utils/kimi.test.js` 与 `admin-web/src/views/Profiles.test.js` 测试。

## 验证

- 前端测试：`pnpm test` 全部通过。
- 构建验证：`pnpm build` 成功。
- 代码质量：`pnpm lint`、`pnpm format:check`、`pnpm type-check` 全部通过。

---

## 背景

Kimi-code（`kimi-for-coding`）对外同时提供 Anthropic Messages 和 OpenAI-compatible 两类端点。参考社区实现 [`opencode-kimi-full`](https://github.com/lemon07r/opencode-kimi-full)，使用 OpenAI-compatible 端点时，需要额外发送一组与 `kimi-cli` 一致的指纹请求头，否则可能被后端拒绝。

本计划的目标是在**不改变后端通用代理逻辑**的前提下，通过 admin-web 前端提供 Kimi-code 配置模板，使其可以一键接入 damning-proxy，输出与其他 OpenAI 兼容上游完全相同的协议。

---

## 目标

1. 在管理后台「上游配置 / Profile」页面提供 `Kimi-code` 预设。
2. 选择预设后自动填充地址、默认模型、特殊请求头。
3. 设备 ID 在表单中可见、可编辑，最终随 `customHeaders` 保存到后端。
4. 动态请求体字段（`prompt_cache_key`、`thinking`、`reasoning_effort`）由客户端请求自带，后端只透传。

---

## 约束

- 不实现、不讨论任何安全相关项。
- 后端保持通用 OpenAI 代理，**不新增 entity 字段、不新增 provider 类型、不改动 Java 代码**。
- 不强制前端覆盖率基线。
- CI lint 保持非阻塞（`continue-on-error: true`）。
- 文档变更需同步中英文版本与目录索引。

---

## 关键决策

| 决策项 | 选择 | 理由 |
|---|---|---|
| 上游协议 | OpenAI-compatible endpoint | 与 damning-proxy 现有代理路径完全兼容，无需协议转换。 |
| 后端改动 | 无 | 复用 `ProxyProfile.customHeaders` 存储特殊头；复用现有 `/chat/completions` 代理路径。 |
| 设备 ID | 前端生成 → 表单输入框 → 保存到后端 `customHeaders` | 浏览器无法读取 `~/.kimi/device_id`，因此首次生成一个无横线 UUID 给用户；用户可改为自己的真实 ID。 |
| 动态体字段 | 由客户端请求自带 | 这些字段与会话/请求级别强相关，不适合写死到 Profile。 |

---

## Kimi-code 特殊请求头

参考 `opencode-kimi-full` 的 `src/headers.ts` 和 `src/constants.ts`：

| Header | 值 | 说明 |
|---|---|---|
| `User-Agent` | `KimiCLI/1.41.0` | 必须保持该前缀，否则后端可能 403。 |
| `X-Msh-Platform` | `kimi_cli` | 平台标识。 |
| `X-Msh-Version` | `1.41.0` | CLI 版本。 |
| `X-Msh-Device-Name` | 主机名（ASCII） | 如 `my-pc`。 |
| `X-Msh-Device-Model` | 设备型号字符串 | 如 `Linux 6.1 x86_64`。 |
| `X-Msh-Device-Id` | UUIDv4 去掉 `-` | 与 `kimi-cli` 的 `~/.kimi/device_id` 共享。 |
| `X-Msh-Os-Version` | 操作系统版本 | 如 `Linux 6.1`。 |

`Authorization: Bearer <token>` 仍然由 `ProxyProfile.bearerToken` 提供，需用户自行准备 Kimi token 并填入。

---

## `prompt_cache_key` 说明

`prompt_cache_key` 是 Kimi-code 请求体中的可选顶层字段，用于**同一会话内多轮对话的 prompt 缓存复用**。通常由客户端把 session ID 作为该字段的值，后端会据此复用已缓存的 prompt 前缀，降低延迟和成本。

本方案中，该字段**不会**在 Profile 的 `customBody` 中写死；调用 damning-proxy 的客户端应在每次请求体中自行传入，后端将原样透传。

---

## 实施步骤

1. **前端工具函数**
   - 新增 `admin-web/src/utils/kimi.js`（或 `.ts`）。
   - 提供 `generateKimiDeviceId()`：生成去掉 `-` 的 UUIDv4。
   - 提供 `buildKimiHeaders(deviceId)`：返回 7 个特殊请求头对象。

2. **Profiles.vue 增加 Kimi-code 预设**
   - 在「上游配置」表单中增加「提供商」下拉框，选项：
     - `Generic OpenAI-compatible`（默认，无预设）
     - `Kimi-code`
   - 选择 `Kimi-code` 时：
     - 填充 `baseUrl` = `https://api.kimi.com/coding/v1`
     - 填充 `defaultModel` = `kimi-for-coding`
     - 生成并显示 Device ID 输入框（可编辑）
     - 根据 Device ID 和浏览器环境生成其他 `X-Msh-*` 头，并写入 `customHeaders` JSON 编辑器
   - 用户仍可手动修改 `customHeaders`、`bearerToken` 等字段。

3. **Device ID 管理**
   - 在 Kimi-code 预设区域增加显式输入框，绑定到 `customHeaders["X-Msh-Device-Id"]`。
   - 切换预设时，如果 `customHeaders` 中尚无该字段，则生成新值填入。
   - 保存时，Device ID 随 `customHeaders` 一起进入后端数据库。

4. **动态字段透传**
   - 后端无需改动；客户端请求体中若包含 `prompt_cache_key`、`thinking`、`reasoning_effort`，通用代理会原样转发到 Kimi。

5. **测试**
   - 新增 `admin-web/src/utils/__tests__/kimi.spec.js`：验证 UUID 格式、header 对象结构、ASCII 过滤等。
   - 在 `Profiles.vue` 测试中补充：选择 Kimi-code 预设后，baseUrl、model、customHeaders 是否正确填充。

6. **文档**
   - 新增本文档 `docs/99-reference/kimi-code-integration-plan.md`（中英文）。
   - 实现完成后，视情况在 `docs/04-api/01-proxy-api.md` 和 `docs/05-guides/03-connect-opencode.md` 中补充 Kimi-code 配置示例。

---

## 测试计划

| 测试 | 范围 | 验证点 |
|---|---|---|
| `kimi.spec.js` | 工具函数 | `generateKimiDeviceId()` 输出 32 位十六进制；`buildKimiHeaders()` 包含 7 个必要头且非 ASCII 被过滤。 |
| `Profiles.vue` 测试 | 前端视图 | 选择 Kimi-code 后，baseUrl、defaultModel、customHeaders 自动填充；修改 Device ID 后 customHeaders 同步更新。 |
| 手动冒烟 | 端到端 | 创建 Kimi-code Profile 和 Instance，通过 `/v1/proxy/{slug}/chat/completions` 发起请求，确认上游收到正确的特殊请求头。 |

---

## 风险与后续

- **风险**：浏览器生成的 `X-Msh-Device-Id` 与真实 `kimi-cli` 的 `~/.kimi/device_id` 不一致，但用户可随时手动覆盖；指纹其他字段（设备型号、OS 版本）也只能按浏览器环境做最佳近似。
- **后续**：如果未来需要为多个 Kimi 模型做模型发现、OAuth 设备流登录、或 Anthropic Messages 协议转换，再考虑在后端引入 provider 类型或专用上游处理模块。
