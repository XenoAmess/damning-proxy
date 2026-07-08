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
| P2-3 | ✓ 2026-07-07 | 插件系统 | Groovy/JS 插件沙箱 / 白名单 | 脚本拥有完整 JVM 访问权限。已新增 `PluginSandbox` 组件，默认启用；Groovy 通过 `SecureASTCustomizer` 在编译期拦截禁止的 import 和 receiver，JS 通过 Nashorn `ClassFilter` 阻止访问被禁 Java 类；支持通过 `application.properties` 调整禁止列表或关闭沙箱。
| P2-4 | ✓ 2026-07-07 | 插件系统 | 插件脚本版本历史 | 保存脚本快照，支持查看与回滚。已新增 `PluginScriptRevision` 实体和 `GET /api/plugins/{id}/revisions`、`POST /api/plugins/{id}/revisions/{revisionId}/rollback` 接口；admin-web 插件编辑器增加「版本历史」面板，支持预览与回滚。 |
| P2-5 | ✓ 2026-07-07 | 管理后台 | Dashboard 图表页 | 基于后端聚合的日志指标展示请求、错误、token 趋势。已新增 `GET /api/metrics/{summary,time-series,top-instances,status-distribution}`，以及 admin-web `Dashboard.vue`（使用 ECharts）。 |
| P2-6 | ✓ 2026-07-07 | 管理后台 | 日志过滤导出 CSV/JSON | 当前日志页仅支持清理。已增加 `GET /api/logs/export?format=json|csv`，支持所有过滤器参数，最多导出 10000 条；CSV 含表头，数据正确转义。 |
| P2-7 | ✓ 2026-07-07 | 运维 | H2 热备份/恢复接口 | 提供 admin API 触发 `BACKUP` 和恢复。已新增 `POST /api/admin/database/backup`（H2 热备份到 `~/.damning-proxy/backups/`）和 `POST /api/admin/database/restore`（验证并暂存恢复文件，因 H2 文件锁需重启后生效）。 |
| P2-8 | ✓ 2026-07-07 | 运维 | 限流响应头 | 返回 `RateLimit-Remaining`、`RateLimit-Reset` 等标准头。已添加 `RateLimitInfo` 记录和 `getRateLimitInfo()` 方法，所有代理端点响应均携带 `RateLimit-Limit`、`RateLimit-Remaining`、`RateLimit-Reset` 头。 |
| P2-9 | ✓ 2026-07-07 | 代码质量 | `skip.frontend.build` 属性文档化 | 已确认属性在 `pom.xml:186` 生效，构建文档中更正了跳过前端的命令为 `-Dskip.frontend.build=true`。 |
| P2-10 | ✗ 已移除 2026-07-08 | 代码质量 | Native image 反射配置 | ~~插件引擎和脚本缓存依赖反射，需补充 `reflect-config.json` 并验证 native build。~~ 已决定**不支持 GraalVM Native Image**：Groovy / JavaScript 动态脚本引擎与 native-image 的闭世界假设冲突，无法可靠运行，因此回退相关改动，并在文档中明确声明不支持。 |

---

## 建议执行顺序

1. **P0 三项**：先修复真实 bug，再补回归测试，确保行为正确。
2. **P1-1（健康检查）、P1-3（DB 索引）、P1-8/P1-9（测试）**：稳定化核心链路。
3. **P1-11（Docker）、P1-12（指标）、P1-7（前端工具链）**：迈向可生产部署。
4. **P2 项**：按实际业务需求逐步纳入。

---

## 下一阶段计划

> 基于 2026-07-08 代码审计，聚焦**非安全项**的性能、正确性、可维护性与文档/工程效率。

### N1 — 性能 / 正确性 / 可维护性

| # | 状态 | 类别 | 标题 | 说明 |
|---|---|------|------|------|
| N1-1 | ✓ | 核心代理 | RateLimiter 缓存 GlobalSettings | 当前 `tryAcquire` 每次查 H2。改为内存缓存，admin 更新 `GlobalSettings` 时刷新。 |
| N1-2 | ✓ | 核心代理 | Metrics SQL 方言中立 | 当前用 H2-only `FORMATDATETIME`。改为 JPQL/HQL 或方言中性函数，支持切换 PostgreSQL/MySQL。 |
| N1-3 | ✓ | 代码质量 | 日志按天清理改为批量删除 | 当前 `deleteOlderThan` 先 `listAll()` 再逐条删除。改为 `DELETE FROM TrafficLog WHERE requestTime < :cutoff`。 |
| N1-4 | ✓ | 代码质量 | slug 严格校验 | 当前只判空。统一使用 `^[a-zA-Z0-9_-]+$`，覆盖 profile / instance / plugin / group。 |
| N1-5 | ✓ | 核心代理 | 熔断器仅把 2xx 记为成功 | 当前只要没抛异常就算 success。应仅 2xx 记 success，4xx/5xx 记 failure。 |
| N1-6 | ✓ | 插件系统 | 插件缓存 key 用 SHA-256 | 当前用 `String.hashCode()`，可能碰撞。改为 SHA-256 内容摘要。 |
| N1-7 | ✓ | 插件系统 | 插件执行线程池有界 | 当前用 `newCachedThreadPool()`。改为 bounded executor 或注入 Quarkus ManagedExecutor。 |
| N1-8 | ✓ | 插件系统 | 上传/导入流式大小限制 | multipart / zip 直接读入内存。加 max file size、max entry size、流式边界校验。 |
| N1-9 | ✓ | 代码质量 | StartupMigration 不覆盖样本插件 | 每次启动覆盖样本脚本。改为样本数据只初始化一次，存在即跳过。 |
| N1-10 | ✓ | 核心代理 | 上游错误体透传 | 重试耗尽后返回空 502/504。把上游 body 带回 `WebApplicationException` 响应。 |

### N2 — 文档 / 工程效率 / Backlog

| # | 状态 | 类别 | 标题 | 说明 |
|---|---|------|------|------|
| N2-1 | — | 文档 | 文档与代码同步审计 | README/数据模型/流程图缺 `GlobalSettings`、`PluginScriptRevision`、audio 端点、metrics 等。Admin API 已补齐。 |
| N2-2 | — | 管理后台 | admin-web 前端测试 | 引入 Vitest + Vue Test Utils，覆盖核心视图。 |
| N2-3 | ✓ | 代码质量 | 前端统一锁文件 | 当前 `package-lock.json` + `pnpm-lock.yaml` 并存。统一用 pnpm 并删除 `package-lock.json`；Maven 的 `frontend-maven-plugin` 改用 pnpm。 |
| N2-4 | ✓ | 插件系统 | JS 引擎复用 ThreadLocal 缓存 | 当前每次执行新建 Nashorn engine。改为复用 ThreadLocal 缓存。 |
| N2-5 | ✓ | 运维 | 文档化分布式状态限制 | 熔断/限流状态仅内存，多副本不一致。文档说明或提供可选 Redis 扩展。 |
| N2-6 | ✓ | 运维 | Dockerfile 缓存 Maven 依赖层 | 加 `mvn dependency:go-offline` 层，避免每次构建重新下载依赖。 |
| N2-7 | ✓ | 文档 | 补齐 Admin API 文档 | 缺 revisions、settings、plugin entries、rate-limit headers 等。 |

### 建议执行顺序

1. **N1-1 / N1-2 / N1-3**：小改动，性能与可移植性收益大。
2. **N1-5 / N1-6 / N1-9**：正确性，避免误判。
3. **N1-4 / N1-7 / N1-8**：边界与稳定性。
4. **N2 文档与工程效率**。

