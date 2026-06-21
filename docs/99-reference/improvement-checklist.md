# 项目优化与改进清单

> 审计日期：2026-06-21  
> 实施完成日期：2026-06-21  
> 二次扫描日期：2026-06-21  
> 涵盖范围：后端 Java (Quarkus) + 前端 Vue 3 admin-web

---

## 第一轮（已完成）

全部 47 个改进点已实现，共 30 个 commits。详见 git log `d3e92ee..c33e91c`。

| 类别 | 数量 | 摘要 |
|------|------|------|
| 线程安全 | 3 | `ConcurrentHashMap`, thread-safe `PluginContext`, heartbeat scheduler 复用 |
| Bug 修复 | 2 | SSL `setSsl(true)` 移除, import 时 `packagePath` 时序修正 |
| 性能 | 8 | `HttpClient` 复用, ScriptEngine ThreadLocal, N+1 批量查询, 路由懒加载, icons tree-shaking, localStorage debounce, deepCopy 优化, 双序列化消除 |
| 代码重复 | 6 | 后端模板方法 + Validation + PanacheUtils, 前端 shared utils |
| 新功能 | 14 | 脚本超时, 熔断器, 限流器, 日志保留, 分页元数据, health DB 检查, 流式 tool_calls, axios 拦截器, 404 路由, session 修剪, save loading, AbortController, cancel/close 检测 |
| 代码质量 | 9 | 资源文件外置, 静默 catch 修复, ProfileForm DTO, 死代码/死文件删除, lang="zh-CN", package-lock.json 排除 |
| 可访问性 | 3 | ARIA roles, keyboard nav, focus trap |

已跳过：`#10` (worker 线程池已足够), `#34` (TypeScript 迁移风险高), `#47` (已在 `.gitignore`)

---

## 第二轮（已完成 14/20）

共 10 个 commits，详见 git log `a9167d7..2065413`。

### High（4/4 ✓）

| # | 状态 | 说明 |
|---|------|------|
| R1 | ✓ | 3 处 ExecutorService 添加 @PreDestroy shutdownNow() |
| R2 | ✓ | ThreadLocal Nashorn 引擎在 finally 中 remove() |
| R3 | ✓ | ProfileUpdate 添加 slug 唯一性冲突检查 |
| R4 | ✓ | Group export 添加 plugin/script null filter |

### Medium（6/6 ✓）

| # | 状态 | 说明 |
|---|------|------|
| R5 | ✓ | 恢复缺失的 4 个图标导入 |
| R6 | ✓ | console.error 改为 ElMessage.warning |
| R7 | ✓ | 引入 DOMPurify 消毒 marked.parse() 输出 |
| R8 | ✓ | token/config 改用 sessionStorage |
| R9 | ✓ | SSE chunk 解析和文件删除添加日志 |
| R10 | ✓ | ensureCompiled 移入 executor 线程避免双编译 |

### Low（4/10 ✓, 6 项标记为 "后期"）

| # | 状态 | 说明 |
|---|------|------|
| R11 | 后期 | HashMap 残留 — 功能安全，按需统一 |
| R12 | ✓ | 删除 PluginContext 中未使用的 ArrayList import |
| R13 | ✓ | ZipBuilder 去 @ApplicationScoped + 删除未用方法 |
| R14 | 后期 | JSON 导入 mode 丢失 — 非关键路径 |
| R15 | ✓ | executor lambda 添加 try-catch 错误日志 |
| R16 | 后期 | ExecutorService CDI producer — Quarkus 默认管理 |
| R17 | 后期 | @Transactional 一致性 — 纯装饰性 |
| R18 | 后期 | RateLimiter 弱一致性注释 — 功能安全 |
| R19 | 后期 | 导出未选中提示 — 已有全量导出语义 |
| R20 | ✓ | stopStreaming 重置 typewriterBuffer/Target |
