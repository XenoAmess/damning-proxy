[English Version](changelog.en.md)

# 变更日志

> 最后更新：2026-07-06  
> 对应源码版本：当前工作区

## 2026-07-06

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
