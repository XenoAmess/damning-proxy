[English Version](improvement-plan.en.md)

# 项目优化与落地计划

> 审计日期：2026-07-07  
> 涵盖范围：后端 Java (Quarkus) + 前端 Vue 3 admin-web  
> 状态：动态维护，完成项请及时更新进度

---

## 说明

本计划基于当前代码、文档、近期提交和运行时行为梳理，按“必须立即修复 → 近期应该补齐 → 按需 backlog”三级优先级排列。每个条目都尽量保持单一、可提交、可验证，方便拆分为独立 issue / PR 或 sprint 任务。

---

## P0 — 立即修复（运行时 bug / 数据正确性问题）

| # | 状态 | 类别 | 标题 | 问题与落地方式 |
|---|------|------|------|----------------|
| P0-1 | ✓ 2026-07-07 | 核心代理 | 非流式上游错误返回 HTTP 500 | `OpenAiProxyService.proxyRequest()` 捕获上游 `WebApplicationException`（502/504）后，又包成 `RuntimeException` 抛出，被 `GlobalExceptionMapper` 转成 500。已改为抛出 `WebApplicationException` 并透传状态码。 |
| P0-2 | ✓ 2026-07-07 | 核心代理 | Profile 熔断字段保存失效 | 前端已支持填写 `circuitBreakerFailureThreshold` 和 `circuitBreakerOpenTimeoutSeconds`，但后端 `ProfileForm` 与 `PanacheProfileRepository.save()` 的字段复制未包含它们。已在 DTO、Export/Import 及 Repository 字段复制中补全。 |
| P0-3 | ✓ 2026-07-07 | 测试 | 非流式上游 502/504 回归测试 | `ProxyApiTest` 已新增 `shouldReturn502WhenUpstreamConnectionRefused` 和 `shouldReturn504WhenUpstreamTimesOut`；`AdminApiTest` 新增 `shouldPersistCircuitBreakerFieldsOnProfileUpdate`。 |

---

## P1 — 近期应该补齐

| # | 状态 | 类别 | 标题 | 价值与落地方式 |
|---|------|------|------|----------------|
| P1-1 | ✓ 2026-07-07 | 核心代理 | 健康检查真正探测上游 | 当前 `/v1/health` 仅检查数据库和熔断快照。已增加对每个启用 profile 的 `baseUrl + /models` 进行 5 秒超时 GET 探测，并返回每个上游的 `up`/`down`/`disabled` 状态。整体 `status` 只有在 DB 与所有启用上游都正常时才为 `ok`。 |
| P1-2 | ✓ 2026-07-07 | 核心代理 | 上游瞬态失败重试/退避 | 目前只有熔断器反应，缺重试。已增加可配置的重试次数和指数退避（`damning-proxy.upstream.max-retries`、`retry-base-delay-ms`、`retry-max-delay-ms`、`retry-status-codes`、`retry-on-timeout`）。非流式和流式上游请求均支持重试。 |
| P1-3 | ✓ 2026-07-07 | 核心代理 | `TrafficLog` 常用查询加索引 | `PanacheLogRepository` 按 `instanceId`、`profileId`、`requestPath`、`requestTime`、`responseStatus` 过滤。已在这五个列上添加 `@Table` 索引，避免日志量增长后查询退化。 |
| P1-4 | ✓ 2026-07-07 | 插件系统 | ZIP 插件包大小限制与结构校验 | 当前只校验路径规范化，缺大小限制、入口文件 `main.groovy`/`main.js` 检查。已增加 `damning-proxy.plugin.zip.max-size-bytes`、`max-entries`、`max-entry-size-bytes` 配置，`PluginPackageStorage.storePackage()` 在持久化前校验单包大小、条目数、单条目大小、路径穿越及入口文件存在性。`PluginAdminApi` 捕获校验异常并返回 400。 |
| P1-5 | ✓ 2026-07-07 | 插件系统 | 插件脚本执行超时配置化 | Groovy/JS 引擎硬编码 30 秒。已增加 `damning-proxy.plugin.timeout-ms` 配置项，Groovy 和 JS 引擎均使用配置值；保留默认 30 秒并补充超时测试。 |
| P1-6 | ✓ 2026-07-07 | 管理后台 | 增加全局限流配置页 | 当前限流只能通过 `application.properties` 配置。已新增 `GlobalSettings` 表、`/api/settings/rate-limit` 读写端点，以及 admin-web「系统设置」页面，修改后实时生效。 |
| P1-7 | ✓ 2026-07-07 | 管理后台 | 前端接入 ESLint / Prettier / 类型检查 | `admin-web/package.json` 缺少 lint、format、type-check 脚本。已新增 ESLint 9 + `eslint-plugin-vue` + `eslint-config-prettier`、Prettier、`vue-tsc` + `tsconfig.json`；统一格式化全部源码并修复 prop 修改等真实问题；CI 已纳入 lint、type-check、format-check。 |
| P1-8 | ✓ 2026-07-07 | 测试 | 为 `RateLimiter`、`CircuitBreaker`、`UpstreamHttpClient` 补单测 | 这些核心韧性组件仅通过 `ProxyApiTest` 间接测试。已补充纯单元测试，模拟时间和失败场景。 |
| P1-9 | ✓ 2026-07-07 | 测试 | 插件真实代理端到端测试 | 缺少验证“请求阶段插件改写上游请求 / 响应阶段插件改写返回体”的测试。已用 WireMock + 真实插件做端到端验证。 |
| P1-10 | ✓ 2026-07-07 | 文档 | 修正插件缓存过时说明 | `docs/05-guides/02-plugin-development.md` 仍写“修改后需重启服务生效”。实际插件脚本按内容哈希缓存，且 `PluginAdminApi` 在更新插件时会调用 `evictCache`；已更新为中英文说明，明确保存后下次请求自动生效。 |
| P1-11 | ✓ 2026-07-07 | 运维 | 提供 Dockerfile 与 docker-compose | 文档仅有手工 Docker 片段。已新增多阶段 `Dockerfile`（Maven + pnpm 构建前端，Eclipse Temurin 21 JRE 运行）和 `docker-compose.yml`，H2 数据通过 volume 挂载到 `/data` 持久化；运行文档已同步更新。 |
| P1-12 | ✓ 2026-07-07 | 运维 | 接入 Prometheus / Micrometer 指标 | 监控文档仍写未集成。已增加 `quarkus-micrometer`，暴露请求量、错误、延迟、token 用量、熔断状态等指标。`/q/metrics` 提供 Prometheus 格式指标；监控文档已更新。 |
| P1-13 | ✓ 2026-07-07 | 代码质量 | 按日志保留天数清理 | 当前仅按数量保留。已增加 `damning-proxy.log.max-age-days` 配置（默认 30 天），`pruneOldLogs` 在数量清理后额外执行按天数清理。 |
| P1-14 | ✓ 2026-07-07 | 代码质量 | Admin API 统一异常处理 | `PluginAdminApi` 与插件引擎仍用 `RuntimeException` 抛 IO/JSON 错误，最终 500。已全部替换为 `WebApplicationException` 或直接返回 `Response.status(500)`，并携带明确错误信息；插件引擎的 `RuntimeException` 已补充原因消息。 |
| P1-15 | ✓ 2026-07-07 | 代码质量 | 前端路由/代码拆分降低 chunk 体积 | 当前 `index.js` 接近 1 MB。已配置 `manualChunks`，将 Element Plus（948K）、通用依赖（422K）、CodeMirror（402K）拆分为独立 vendor chunk；主入口降至 6 KB，CodeMirror 按需懒加载。 |

---

## P2 —  backlog / 按需实现

| # | 状态 | 类别 | 标题 | 说明 |
|---|------|------|------|------|
| P2-1 | ✓ 2026-07-07 | 核心代理 | 补齐 `/audio/*` 代理端点 | 支持 Whisper / TTS 类模型，补齐 OpenAI 协议覆盖。已新增 `POST /v1/proxy/{instance}/audio/speech`（JSON 入参、二进制音频返回）、`/audio/transcriptions` 与 `/audio/translations`（multipart 文件上传）；`UpstreamHttpClient` 新增 `sendBinary()` 与 `sendMultipart()` 方法。 |
| P2-2 | ✓ 2026-07-07 | 核心代理 | 流式响应按 chunk 跑插件 | 当前响应插件仅在流完全结束后执行。已新增 `STREAM_CHUNK` 执行阶段；`OpenAiProxyService` 在流式 chat completions 的每个 SSE chunk 上执行 `STREAM_CHUNK` 阶段插件，支持实时修改或过滤 chunk。 |
| P2-3 | — | 插件系统 | Groovy/JS 插件沙箱 / 白名单 | 脚本拥有完整 JVM 访问权限，引入 ClassShutter 或安全策略限制文件/网络访问。 |
| P2-4 | — | 插件系统 | 插件脚本版本历史 | 保存脚本快照，支持查看与回滚。 |
| P2-5 | ✓ 2026-07-07 | 管理后台 | Dashboard 图表页 | 基于后端聚合的日志指标展示请求、错误、token 趋势。已新增 `GET /api/metrics/{summary,time-series,top-instances,status-distribution}`，以及 admin-web `Dashboard.vue`（使用 ECharts）。 |
| P2-6 | ✓ 2026-07-07 | 管理后台 | 日志过滤导出 CSV/JSON | 当前日志页仅支持清理。已增加 `GET /api/logs/export?format=json|csv`，支持所有过滤器参数，最多导出 10000 条；CSV 含表头，数据正确转义。 |
| P2-7 | ✓ 2026-07-07 | 运维 | H2 热备份/恢复接口 | 提供 admin API 触发 `BACKUP` 和恢复。已新增 `POST /api/admin/database/backup`（H2 热备份到 `~/.damning-proxy/backups/`）和 `POST /api/admin/database/restore`（验证并暂存恢复文件，因 H2 文件锁需重启后生效）。 |
| P2-8 | ✓ 2026-07-07 | 运维 | 限流响应头 | 返回 `RateLimit-Remaining`、`RateLimit-Reset` 等标准头。已添加 `RateLimitInfo` 记录和 `getRateLimitInfo()` 方法，所有代理端点响应均携带 `RateLimit-Limit`、`RateLimit-Remaining`、`RateLimit-Reset` 头。 |
| P2-9 | ✓ 2026-07-07 | 代码质量 | `skip.frontend.build` 属性文档化 | 已确认属性在 `pom.xml:186` 生效，构建文档中更正了跳过前端的命令为 `-Dskip.frontend.build=true`。 |
| P2-10 | — | 代码质量 | Native image 反射配置 | 插件引擎和脚本缓存依赖反射，需补充 `reflect-config.json` 并验证 native build。 |

---

## 建议执行顺序

1. **P0 三项**：先修复真实 bug，再补回归测试，确保行为正确。
2. **P1-1（健康检查）、P1-3（DB 索引）、P1-8/P1-9（测试）**：稳定化核心链路。
3. **P1-11（Docker）、P1-12（指标）、P1-7（前端工具链）**：迈向可生产部署。
4. **P2 项**：按实际业务需求逐步纳入。
