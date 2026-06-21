# 项目优化与改进清单

> 审计日期：2026-06-21  
> 涵盖范围：后端 Java (Quarkus) + 前端 Vue 3 admin-web

---

## 一、安全 (Critical)

| # | 问题 | 位置 | 建议 |
|---|------|------|------|
| 1 | XSS 风险 — `marked.parse()` 渲染 Markdown 无 sanitizer，可注入 `<script>` | `admin-web/src/views/Chat.vue` `Logs.vue` | 配置 `marked` 的 sanitizer 或引入 DOMPurify |
| 2 | API 无鉴权 — 所有 `/api/` 管理端点完全开放 | `api/admin/*.java` | 添加认证/授权中间件 |
| 3 | `bearerToken` 明文存储 — DB 和 API 响应中均暴露 | `entity/ProxyProfile.java`, `api/admin/ProfileAdminApi.java` | 存储时加密，API 响应中脱敏 |
| 4 | 插件无沙箱 — Groovy/JS 脚本可执行任意 JVM 操作 | `plugin/engine/GroovyPluginEngine.java`, `JavaScriptPluginEngine.java` | 添加 `SecureASTCustomizer` / ClassFilter / 沙箱限制 |

---

## 二、线程安全 Bug (Critical)

| # | 问题 | 位置 | 建议 |
|---|------|------|------|
| 5 | `GroovyPluginEngine.scriptClassCache` 使用 `HashMap` — 并发访问可能数据损坏 | `plugin/engine/GroovyPluginEngine.java` | 改为 `ConcurrentHashMap` |
| 6 | `PluginContext` / `FriendlyLogCollector` 使用非线程安全集合 — 流式路径存在 data race | `plugin/PluginContext.java`, `plugin/FriendlyLogCollector.java` | 改为线程安全集合或确保单线程访问 |
| 7 | 流式请求每次创建 `ScheduledExecutorService` — 线程泄漏风险 | `proxy/OpenAiProxyService.java:263` | 复用共享的 scheduled executor |
| 8 | SSL 全局硬编码 `setSsl(true)` — HTTP-only 上游会失败 | `proxy/UpstreamHttpClient.java:45` | 根据上游 URL scheme 动态设置 SSL |

---

## 三、性能 (High)

| # | 问题 | 位置 | 建议 |
|---|------|------|------|
| 9 | `sendStream()` 每次创建新 `HttpClient` — 每个流式请求新建连接池，资源浪费严重 | `proxy/UpstreamHttpClient.java:152` | 复用单个 `HttpClient` 实例 |
| 10 | `send()` 阻塞 worker 线程 — `CompletableFuture.get()` 最长等待 1 小时 | `proxy/UpstreamHttpClient.java` | 使用 Mutiny 响应式或异步回调，避免阻塞 |
| 11 | JavaScript `ScriptEngine` 每次执行都创建 — Nashorn 引擎创建开销大 | `plugin/engine/JavaScriptPluginEngine.java:44` | 使用 ThreadLocal 或对象池复用 |
| 12 | N+1 查询 — 实例/插件组导出需额外 DB 查询 per entity | `api/admin/InstanceAdminApi.java`, `api/admin/PluginGroupAdminApi.java` | 批量 fetch 或在 Repository 层提供 join 查询 |
| 13 | `buildUri()` 同一请求调用 3 次 | `proxy/UpstreamHttpClient.java:126-129` | 提取为局部变量 |
| 14 | 前端无懒加载 — 所有视图 eager import，CodeMirror ~200KB 首次即加载 | `admin-web/src/router.js` | 使用 `() => import(...)` 动态导入 |
| 15 | 所有 Element Plus Icons 全局注册 — 仅 ~12 个实际使用 | `admin-web/src/main.js` | 按需引入，利用 tree-shaking |
| 16 | Chat 流式响应期间 `deep: true` watcher 频繁触发 localStorage 写入 | `admin-web/src/views/Chat.vue` | 使用 debounce 或 shallow watcher |
| 17 | `deepCopy()` 序列化+反序列化整个 body per-plugin 执行 — O(plugins × bodySize) | `plugin/PluginExecutionService.java` | 考虑按需深拷贝或引用计数 |
| 18 | `TrafficLogService.recordRequest()` 对 body 执行两次序列化 | `service/TrafficLogService.java` | 复用第一次序列化结果计算长度 |

---

## 四、代码重复 (Medium)

| # | 问题 | 位置 | 建议 |
|---|------|------|------|
| 19 | `chatCompletions()` 和 `listModels()` ~90% 结构相同 | `proxy/OpenAiProxyService.java` | 抽为模板方法 |
| 20 | 6 份相同的插件脚本模板嵌入 Java 字符串 | `migration/StartupMigration.java` | 外置为资源文件，按参数插值 |
| 21 | slug 校验 / entity save 字段拷贝 模式在所有 AdminApi / PanacheRepo 中重复 | `api/admin/*.java`, `repository/panache/*.java` | 抽为公共工具类 |
| 22 | 前端导出/导入/删除逻辑在 5 个 View 中基本重复 | `admin-web/src/views/*.vue` | 抽为 composables 或工具函数 |
| 23 | `isStreamingRequest()` 在 ProxyApi 和 OpenAiProxyService 中重复定义 | `proxy/ProxyApi.java`, `proxy/OpenAiProxyService.java` | 保留一处，另一处引用 |
| 24 | `parseThink()` / `formatBytes()` 在多组件中重复 | `Chat.vue`, `Logs.vue`, `Plugins.vue` | 抽为共享工具函数 |

---

## 五、缺失功能 (Medium)

| # | 问题 | 建议 |
|---|------|------|
| 25 | 无脚本执行超时 — Groovy/JS 死循环会永久阻塞请求 | 添加脚本执行超时 (如 30s)，超时时中断并返回错误 |
| 26 | 无上游断路器 (circuit breaker) — 上游故障时无退避/熔断 | 引入 Resilience4j 或自定义断路器 |
| 27 | 无请求限流 (rate limiting) | 按 instance/id 或 IP 维度限流 |
| 28 | 无日志保留/清理策略 — TrafficLog 无限增长 | 添加定期清理 job 或配置最大保留条数/天数 |
| 29 | 列表接口无分页元数据 (total count) — 前端无法分页 | 在分页查询返回 total 字段 |
| 30 | Health check 不检查 DB 连接 | `/v1/health` 增加 DB 连通性检查 |
| 31 | 流式响应支持不足 — `/v1/models` 不支持，`buildStreamingResponseBody()` 丢失 tool_calls 和多 choice | 完善流式支持 |
| 32 | 前端无全局错误拦截器 — 401/500/网络错误无统一处理 | 在 axios 实例添加 response interceptor |
| 33 | 前端无路由守卫和 404 页面 | 添加 `beforeEach` 守卫和通配路由 |
| 34 | 前端无 TypeScript / 无测试 | 逐步迁移到 TypeScript，引入 Vitest |
| 35 | 前端 Chat sessions 存储在 localStorage 无限增长 | 添加 pruning 机制，限制 session 数量和大小 |
| 36 | 前端 save 按钮无 `:loading` 状态 — 可重复点击 | 所有 CRUD dialog 的保存按钮添加 loading 状态 |
| 37 | 前端无 AbortController — 流式请求无法取消 | 添加 AbortController 支持 |
| 38 | `PluginAdminApi.import` bug — 返回给前端的 `packagePath` 为 stale null | 修复：保存后重新 fetch 或刷新字段 |

---

## 六、代码质量与可维护性 (Low-Medium)

| # | 问题 | 位置 | 建议 |
|---|------|------|------|
| 39 | `StartupMigration` 中大段中文字符串硬编码，难以维护和国际化 | `migration/StartupMigration.java` | 外置到 `.properties` 或 `.txt` 资源文件 |
| 40 | 错误处理不一致 — 多处 `catch (e) { /* ignore */ }` 静默吞错 | `Chat.vue`, `Logs.vue`, `Plugins.vue` 等 | 至少记录日志或显示用户提示 |
| 41 | cancel 检测使用字符串比较 `e !== 'cancel'` — 依赖 Element Plus 内部实现 | 5 个 View 的 `remove()` | 使用 boolean flag 或 `instanceof Error` |
| 42 | `ProxyProfile.update()` 直接使用请求 body 作为 JPA 实体 — 潜在注入风险 | `api/admin/ProfileAdminApi.java` | 使用 DTO 解耦，仅拷贝白名单字段 |
| 43 | `UpstreamHttpClient.isStreamingRequest()` 定义但从未被调用 — 死代码 | `proxy/OpenAiProxyService.java:518` | 删除或确认用途后保留 |
| 44 | `OpenAiProxyService.toJson()` 被标记 `@SuppressWarnings("unused")` — 死代码 | `proxy/OpenAiProxyService.java` | 删除 |
| 45 | 前端 `style.css` (296 行) 从未被 import — 死代码 | `admin-web/src/style.css` | 删除 |
| 46 | 前端 `package-lock.json` 和 `pnpm-lock.yaml` 共存 — 应只保留一个 | `admin-web/` | 删除 `package-lock.json`，统一使用 pnpm |
| 47 | `node/` 目录含独立 Node.js 二进制 — 非标准结构 | `admin-web/node/` | 加入 `.gitignore` 或移除 |

---

## 七、可访问性 (Low-Medium)

| # | 问题 | 位置 |
|---|------|------|
| 48 | `lang="en"` 但所有 UI 为中文 | `admin-web/index.html` |
| 49 | 日志卡片不可键盘导航 — 缺少 `role="button"`, `tabindex`, `@keydown` | `Logs.vue` |
| 50 | 全局缺少 ARIA labels | 所有组件 |
| 51 | Dialog 缺少 focus trap | 所有 `el-dialog` |

---

## 改进优先级建议

### 第一优先级 (本次迭代)
1. 安全 #1 (XSS) — 引入 DOMPurify sanitize Markdown 输出
2. 线程安全 #5 (HashMap 改 ConcurrentHashMap)
3. Bug 修复 #8 (SSL 硬编码)
4. Bug 修复 #38 (packagePath stale null)

### 第二优先级 (近期)
5. 安全 #2, #3, #4 (API 鉴权、token 加密、插件沙箱)
6. 性能 #9, #10, #11 (HttpClient 复用、阻塞改异步、ScriptEngine 复用)
7. 线程安全 #6, #7 (PluginContext 线程化、executor 泄漏)

### 第三优先级 (中期)
8. 代码重复 #19-#24 (重构去重)
9. 缺失功能 #25-#30 (超时、断路器、限流、分页、健康检查)

### 第四优先级 (长期)
10. 前端 TypeScript 化 / 测试覆盖
11. 可访问性改进
12. 国际化

---

> 共计 **51 个改进点**，其中安全 4 个，Bug 4 个，性能 10 个，重复 6 个，缺失功能 14 个，代码质量 9 个，可访问性 4 个。
