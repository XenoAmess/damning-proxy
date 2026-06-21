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

## 第二轮扫描（新发现）

共 **20 个新问题**，按严重程度排列。

### 一、High（4 项）

| # | 问题 | 位置 | 建议 |
|---|------|------|------|
| R1 | ExecutorService 泄漏 — `GroovyPluginEngine`、`JavaScriptPluginEngine`、`OpenAiProxyService` 中的 executor 无 `@PreDestroy` 关闭 | `plugin/engine/GroovyPluginEngine.java:35`, `plugin/engine/JavaScriptPluginEngine.java:43`, `proxy/OpenAiProxyService.java:72` | 添加 `@PreDestroy` 方法调用 `shutdownNow()` |
| R2 | JavaScriptPluginEngine ThreadLocal 内存风险 — 每个线程永久持有 Nashorn ScriptEngine，cached thread pool 线程存活 60s 导致内存累积 | `plugin/engine/JavaScriptPluginEngine.java:36` | 在 `finally` 中调用 `engineCache.remove()` 或使用有大小限制的对象池 |
| R3 | ProfileAdminApi.update() 缺少 slug 唯一性检查 | `api/admin/ProfileAdminApi.java:50-61` | 类似 InstanceAdminApi，update 时检查 slug 冲突并返回 409 |
| R4 | PluginGroupAdminApi.export() 潜在 NPE — `i.plugin.script` 未判空 | `api/admin/PluginGroupAdminApi.java:103` | 添加 `.filter(i -> i.plugin != null)` |

### 二、Medium（6 项）

| # | 问题 | 位置 | 建议 |
|---|------|------|------|
| R5 | Chat.vue 缺少 4 个图标导入 (`User`, `ChatLineRound`, `CopyDocument`, `Paperclip`)，运行时图标不显示 | `admin-web/src/views/Chat.vue:221-224` | 添加缺失的图标到 import 语句 |
| R6 | `console.error` 残留在生产代码中 | `admin-web/src/views/Logs.vue:401` | 替换为 `ElMessage.warning` 或移除 |
| R7 | XSS 风险 — `v-html` 绑定未经消毒的 `marked.parse()` 输出，LLM 响应可注入 `<script>` | `Chat.vue:97,106,131,140`, `Logs.vue:154` | 使用 DOMPurify 消毒 `marked.parse()` 输出 |
| R8 | Bearer token 存储在 localStorage，XSS 可窃取 | `admin-web/src/views/Chat.vue:381-382` | 改用 sessionStorage 或不持久化 token |
| R9 | 静默吞错 — SSE chunk 解析失败和文件删除失败均无日志 | `proxy/OpenAiProxyService.java:326-328`, `plugin/storage/PluginPackageStorage.java:56-58` | 添加 `Log.debugf/warnf` 记录异常信息 |
| R10 | JavaScriptPluginEngine 首次执行时编译两遍 (ensureCompiled + execute) | `plugin/engine/JavaScriptPluginEngine.java:56,86-114` | 将 compilation 移到 executor 线程内执行，或缓存编译结果 |

### 三、Low（10 项）

| # | 问题 | 位置 | 建议 |
|---|------|------|------|
| R11 | 方法内 `HashMap` 用法不一致 — 代码库已改用 `ConcurrentHashMap`，但多处方法局部仍用 `HashMap` | `proxy/OpenAiProxyService.java:311,316`, `service/TrafficLogService.java:129` | 功能安全（栈限定），可选注释或统一为 `ConcurrentHashMap` |
| R12 | 未使用的 import | `plugin/PluginContext.java:3` | 删除 `import java.util.ArrayList` |
| R13 | `ZipBuilder` — 全静态方法但有 `@ApplicationScoped`，未使用的方法 `textEntry()`/`textStream()` | `plugin/storage/ZipBuilder.java` | 删除 `@ApplicationScoped`，添加 private 构造器，移除未使用方法 |
| R14 | `PluginAdminApi.importPluginsJson` 硬编码 mode 为 `SINGLE_SCRIPT`，ZIP 插件 JSON 导入时丢失 mode 信息 | `api/admin/PluginAdminApi.java:211-213` | 在 `ExportPlugin` 中包含 mode 字段 |
| R15 | `recordResponse` 从 executor 线程异步调用，lambda 内无错误处理，失败会被静默吞掉 | `proxy/OpenAiProxyService.java:203-205,211-212` | 在 executor lambda 中添加 try-catch + 日志 |
| R16 | `@Inject ExecutorService` 的 CDI producer 不可见，需确认生命周期管理 | `proxy/OpenAiProxyService.java:69-70` | 确认 Quarkus 管理或添加注释 |
| R17 | 读操作的 `@Transactional` 不一致 | `api/admin/PluginGroupAdminApi.java:32,38` | 统一样式：只对写操作加 `@Transactional` |
| R18 | `RateLimiter.cleanup()` 迭代 `ConcurrentHashMap.values()` 的弱一致性 | `proxy/RateLimiter.java:66-72` | 当前代码正确，添加注释说明 |
| R19 | 导出未选中任何项时静默导出全部数据 | `admin-web/src/views/Instances.vue:221-229` | 添加确认对话框或提示 |
| R20 | `stopStreaming()` 未重置 `typewriterBuffer` 和 `typewriterTarget` | `admin-web/src/views/Chat.vue:545-551` | 在 `stopStreaming()` 中重置这些状态 |
