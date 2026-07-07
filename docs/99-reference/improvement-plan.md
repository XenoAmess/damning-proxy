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
| P1-4 | 待处理 | 插件系统 | ZIP 插件包大小限制与结构校验 | 当前只校验路径规范化，缺大小限制、入口文件 `main.groovy`/`main.js` 检查。限制单包大小并拒绝结构非法文件。 |
| P1-5 | 插件系统 | 插件脚本执行超时配置化 | Groovy/JS 引擎硬编码 30 秒。暴露 `damning-proxy.plugin.timeout-ms` 配置项。 |
| P1-6 | 管理后台 | 增加全局限流配置页 | 当前限流只能通过 `application.properties` 配置。新增 `/api/settings/rate-limit` 读写端点与管理页面。 |
| P1-7 | 管理后台 | 前端接入 ESLint / Prettier / 类型检查 | `admin-web/package.json` 缺少 lint、format、type-check 脚本。接入工具链并纳入 CI。 |
| P1-8 | 测试 | 为 `RateLimiter`、`CircuitBreaker`、`UpstreamHttpClient` 补单测 | 这些核心韧性组件仅通过 `ProxyApiTest` 间接测试。补充纯单元测试，模拟时间和失败场景。 |
| P1-9 | 测试 | 插件真实代理端到端测试 | 缺少验证“请求阶段插件改写上游请求 / 响应阶段插件改写返回体”的测试。用 WireMock + 真实插件做端到端验证。 |
| P1-10 | 文档 | 修正插件缓存过时说明 | `docs/05-guides/02-plugin-development.md` 仍写“修改后需重启服务生效”，实际更新已会失效缓存。 |
| P1-11 | 运维 | 提供 Dockerfile 与 docker-compose | 文档仅有手工片段，应提供可版本控制的 `Dockerfile` 和 `docker-compose.yml`，并挂载 H2 数据卷。 |
| P1-12 | 运维 | 接入 Prometheus / Micrometer 指标 | 监控文档仍写未集成。增加 `quarkus-micrometer`，暴露请求量、错误、延迟、token 用量、熔断状态等指标。 |
| P1-13 | 代码质量 | 按日志保留天数清理 | 当前仅按数量保留。增加 `damning-proxy.log.max-age-days` 配置，按时间窗口清理。 |
| P1-14 | 代码质量 | Admin API 统一异常处理 | `PluginAdminApi` 与插件引擎仍用 `RuntimeException` 抛 IO/JSON 错误，最终 500。转换为 `WebApplicationException` 并携带明确状态码。 |
| P1-15 | 代码质量 | 前端路由/代码拆分降低 chunk 体积 | 当前 `index.js` 接近 1 MB gzip。将 Chat、Logs、PluginEditor 等大屏按路由动态导入。 |

---

## P2 —  backlog / 按需实现

| # | 类别 | 标题 | 说明 |
|---|------|------|------|
| P2-1 | 核心代理 | 补齐 `/audio/*` 代理端点 | 支持 Whisper / TTS 类模型，补齐 OpenAI 协议覆盖。 |
| P2-2 | 核心代理 | 流式响应按 chunk 跑插件 | 当前响应插件仅在流完全结束后执行。按 chunk 修改可实现实时内容过滤。 |
| P2-3 | 插件系统 | Groovy/JS 插件沙箱 / 白名单 | 脚本拥有完整 JVM 访问权限，引入 ClassShutter 或安全策略限制文件/网络访问。 |
| P2-4 | 插件系统 | 插件脚本版本历史 | 保存脚本快照，支持查看与回滚。 |
| P2-5 | 管理后台 | Dashboard 图表页 | 基于 Micrometer 指标展示请求、错误、token 趋势。 |
| P2-6 | 管理后台 | 日志过滤导出 CSV/JSON | 当前日志页仅支持清理，增加导出当前过滤结果。 |
| P2-7 | 运维 | H2 热备份/恢复接口 | 提供 admin API 触发 `BACKUP` 和恢复。 |
| P2-8 | 运维 | 限流响应头 | 返回 `RateLimit-Remaining`、`RateLimit-Reset` 等标准头。 |
| P2-9 | 代码质量 | `skip.frontend.build` 属性文档化 | 确认属性已生效并写入构建文档。 |
| P2-10 | 代码质量 | Native image 反射配置 | 插件引擎和脚本缓存依赖反射，需补充 `reflect-config.json` 并验证 native build。 |

---

## 建议执行顺序

1. **P0 三项**：先修复真实 bug，再补回归测试，确保行为正确。
2. **P1-1（健康检查）、P1-3（DB 索引）、P1-8/P1-9（测试）**：稳定化核心链路。
3. **P1-11（Docker）、P1-12（指标）、P1-7（前端工具链）**：迈向可生产部署。
4. **P2 项**：按实际业务需求逐步纳入。
