[English Version](changelog.en.md)

# 变更日志

> 最后更新：2026-07-08
> 对应源码版本：当前工作区

## 2026-07-08

- 明确声明**不支持 GraalVM Native Image**：Groovy / JavaScript 动态脚本引擎与 native-image 的闭世界假设冲突，已移除相关配置与插件。
- 性能优化：
  - `GlobalSettings` 在内存中缓存，`RateLimiter` 不再每次请求都查 H2。
  - `MetricsService.timeSeries()` 改写为 JPQL + Java 端分桶，支持跨数据库（PostgreSQL/MySQL）迁移。
  - `TrafficLog` 按天清理改为批量删除，避免高日志量下性能问题。
- 正确性修复：
  - 熔断器仅把上游 2xx 响应记为成功，4xx/5xx 记为失败。
  - 移除 `TrafficLog.requestTime` 的 `@CreationTimestamp`，避免手动设置的时间被覆盖。
  - 上游错误响应体重试耗尽后透传到代理响应（`UpstreamHttpClient` 与 `OpenAiProxyService` 中保留 `WebApplicationException` 的 response entity）。
- 插件系统改进：
  - 插件缓存 key 从 `String.hashCode()` 改为 SHA-256 摘要，避免碰撞。
  - 插件执行线程池改为有界 `ThreadPoolExecutor`（可配置 `damning-proxy.plugin.execution.pool-size`），防止无限线程增长。
  - 插件上传与 ZIP 导入增加大小与条目数量限制（`BoundedInputStream`）。
- 代码质量：
  - `slug` 统一使用 `^[a-zA-Z0-9_-]+$` 严格校验，覆盖 profile / instance / plugin / group 的创建与导入。
  - `StartupMigration` 样本插件/插件组只初始化一次，不再覆盖已有数据。
- 文档：
  - 新增《分布式状态限制》运维文档，说明熔断/限流/插件缓存/全局设置缓存仅内存内有效，多副本不共享。
  - 补齐 Admin API 文档：新增 `/api/settings/rate-limit`、插件 dry-run、模板、revisions、rollback、entries、ZIP 导入等接口说明。
  - 更新 Dockerfile：增加 `mvn dependency:go-offline` 层以复用 Maven 依赖缓存。
- 工程效率：
  - 统一前端锁文件为 `pnpm-lock.yaml`，删除 `package-lock.json`。
  - Maven `frontend-maven-plugin` 改用 Node 22.14 + pnpm 11.8，本地构建与 CI 使用同一套工具链。
- 文档同步审计（N2-1）：
  - 更新数据模型文档：新增 `GlobalSettings`、`PluginScriptRevision` 实体，补充 `Plugin` 的 `mode`/`packagePath`/`sample` 字段、`ProxyProfile` 熔断器字段、音频代理端点、请求时间语义。
  - 更新代理流程文档：补充 `/audio/*` 端点说明、修复 `PluginContext` 代码块格式。
  - 更新 README（CN+EN）：移除 Native Image 声明、更新技术栈版本、补充音频端点/限流/熔断/指标/插件 ZIP 包等新特性，前端开发命令改用 pnpm。
- 前端测试（N2-2）：
  - admin-web 引入 Vitest + @vue/test-utils + jsdom。
  - 新增 `vitest.config.js` 与 `ChatParamPanel.test.js` 示例测试。
  - package.json 增加 `pnpm test` / `pnpm test:watch` 脚本。
  - CI 工作流与测试/构建文档已同步增加前端测试步骤。
- 收尾（checklist 6 项 "Later"）：
  - R11/R16/R17：已验证 HashMap 残留（已全替换）、ExecutorService CDI（Quarkus 内置）、@Transactional 调用链（安全）。
  - R14：修复 `PluginAdminApi.ExportPlugin` 缺少 `mode` 字段导致 JSON 导入强制 `SINGLE_SCRIPT`。
  - R18：`RateLimiter` 新增 Javadoc 说明弱一致性与多实例限制。
  - R19：4 个视图导出按钮在未选中项时弹出确认对话框提示全量导出数量。
  - 新增 2 个前端组件单测（`ChatSessionSidebar`、`LogFilterBar`），共 3 文件 11 测试。

## 2026-07-07

- 新增测试覆盖：插件保存校验 400、插件 dry-run、日志批量删除分页。
- 修复 `PanacheLogRepository.deleteOldest` 分批删除时未 flush，导致同一事务内后续批次重复读到已删除记录的问题。
- 插件保存错误提示更明显：后台校验失败返回的 400 错误信息现在会在「插件管理」和「插件调试台」保存时直接展示给用户，不再只进入 friendly snapshot。
- 技术债清理：将核心代理、健康检查、实例导出、日志序列化等模块中剩余的 `HashMap` 替换为 `LinkedHashMap`/`ConcurrentHashMap`，保证输出顺序确定并与项目线程安全实践一致；`@Transactional` 与 `ExecutorService` 生命周期已确认规范，无需额外调整。
- 拆分管理后台 Chat / Logs 页面：将 `Chat.vue`、`Logs.vue` 拆分为多个可复用子组件，降低页面复杂度与维护成本。
- 新增组件目录 `admin-web/src/components/chat/` 与 `admin-web/src/components/logs/`。
- 刷新过期的项目文档：更新架构、代理流程、API 文档、日志与排障文档（中文与英文版本同步）。
- 导入导出增加预览：新增 `ImportPreviewDialog` 组件，配置、实例、插件组、插件 JSON 导入前可预览新增/覆盖数量并二次确认。
- 流量日志新增 token 用量统计：从上游响应 `usage` 字段自动提取 prompt_tokens / completion_tokens / total_tokens，并在日志详情与卡片中展示。
- 新增单元测试 `TrafficLogServiceTest.shouldExtractTokenUsageFromResponse`。
- 新增代理端点：`/v1/proxy/{instanceSlug}/embeddings` 与 `/v1/proxy/{instanceSlug}/images/generations`，兼容 OpenAI embeddings 与 images/generations 接口。
- 新增对应代理测试 `ProxyApiTest.shouldProxyEmbeddings` 与 `ProxyApiTest.shouldProxyImageGenerations`。
- 流式上游失败时返回友好的 SSE 错误：后台对上游连接失败、HTTP 错误状态码等场景统一发送 `event: error` 事件，前端解析后提示具体错误信息，不再静默断流。
- 新增流式代理错误场景测试 `ProxyApiTest.shouldReturnSseErrorOnUpstreamStreamingFailure`。
- 日志清理改批量删除：后台新增 `/api/logs/prune` 接口，支持保留最近 N 条或全部清空；前台「流量日志」页面新增「批量清理」按钮与确认对话框。
- 修复后台日志自动清理在高日志量场景下一次删除过多条目导致的性能问题，`PanacheLogRepository.deleteOldest` 默认按 1000 条批次循环删除。

## 2026-07-06

- `/v1/health` 健康检查新增熔断器状态快照，帮助区分代理自身与上游故障。
- `ProxyProfile` 新增熔断器配置字段：失败阈值与熔断恢复时间，可在管理后台配置。
- 新增流式代理测试 `ProxyApiTest.shouldProxyStreamingChatCompletions`。
- 修复流式代理日志记录：正常 `[DONE]` 结束现在正确记录 200 与汇总后的响应体，不再提前记录为空或 499。
- Chat 消息中的 Markdown 代码块新增悬停「复制」按钮。
- 管理后台 Chat 会话历史从 localStorage 迁移到 IndexedDB，首次启动时自动导入旧数据。
- 管理后台 Chat 页面新增参数面板：支持设置 temperature、top_p、max_tokens 与 system prompt。
- 管理后台 Chat 消息新增「重发」与「重新生成」操作。
- 管理后台流量日志支持按实例、状态、路径关键字、时间范围筛选，并支持分页。
- 新增插件调试器页面：支持 Groovy 语法高亮、样例请求试运行与保存时脚本校验。
- 修复 `PanacheLogRepository` 动态查询中未使用参数导致空条件时报错的问题。

## 0.1.0 — 2026-06-21

首次版本发布，主要变更：

- 版本号由 1.0-SNAPSHOT 改为 0.1.0。
- 新增上游故障熔断器（CircuitBreaker），自动对持续失败的上游进行断路保护。
- 新增代理请求速率限制器（RateLimiter），支持按实例配置请求速率上限。
- 流量日志新增自动清理（log retention / auto-pruning），可按天数或条数配置保留策略。
- 健康检查（HealthResource）新增数据库连通性检测。
- 脚本执行新增 30 秒超时，防止 Groovy/JavaScript 插件无限运行。
- 流式代理响应阶段插件支持 `tool_calls` 的 SSE 转发。
- 多项线程安全修复：核心结构改用 `ConcurrentHashMap`，`PluginContext` 改为线程安全。
- 性能优化：`HttpClient` 实例复用、`ScriptEngine` 缓存、N+1 查询修复、路由懒加载。
- 代码去重与清理。
- 前端无障碍改进（ARIA 属性、键盘导航支持）。

## 2026-06-20

- 后台运行文档改为推荐使用 `screen` 管理 `mvn quarkus:dev`，避免遗留进程占用端口或 H2 锁。
- 更新代理端点文档：
  - `/v1/proxy/{instanceSlug}/chat/completions` 统一为单一路径，根据 `stream` 字段返回 JSON 或 SSE。
  - 补充 SSE 通过 `StreamingOutput` 桥接 `Multi<String>` 的实现说明。
  - 补充请求头/请求体合并优先级、上游超时禁用策略、30 秒心跳诊断。

## 2026-06-17

- 初始化 damning-proxy 知识库。
- 新增项目概览、架构设计、数据模型、代理流程、插件系统、API 文档、构建/运行/自测指南、运维文档。
- 迁移原 `doc/plan.md` 到 `docs/99-reference/plan.md`。
- 建立知识库演进约定：代码变更需同步更新对应文档。

---

## 历史变更

（待补充后续版本）
