# 项目优化与改进清单

> 审计日期：2026-06-21  
> 实施完成日期：2026-06-21  
> 涵盖范围：后端 Java (Quarkus) + 前端 Vue 3 admin-web

---

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
