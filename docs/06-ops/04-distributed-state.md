[English Version](04-distributed-state.en.md)

# 04 分布式状态限制

> 最后更新：2026-07-08
> 对应源码版本：当前工作区

`damning-proxy` 默认以单进程方式运行，所有状态主要分为两类：

- **持久化状态**：存储在数据库中（默认 H2 文件数据库），通过 JPA/Hibernate 访问。
- **进程内状态**：仅存在于单个 JVM 实例的内存中，多个进程之间不共享。

---

## 进程内状态清单

| 状态 | 位置 | 说明 |
|---|---|---|
| 熔断器状态 | `proxy/CircuitBreaker.java` | 每个 `ProxyProfile` 维护一个 `CircuitBreaker.State`，记录连续失败次数、半开/打开状态等。 |
| 限流器计数 | `proxy/RateLimiter.java` | 基于时间窗口的内存计数器，按全局设置 `maxRequestsPerWindow` 与 `windowSeconds` 滑动计算。 |
| 插件编译缓存 | `plugin/engine/GroovyPluginEngine.java`<br>`plugin/engine/JavaScriptPluginEngine.java` | 脚本编译产物（Groovy `Script` 对象、JS `CompiledScript`）缓存在 `ThreadLocal` 或 Guava Cache 中。 |
| 全局设置缓存 | `repository/panache/PanacheGlobalSettingsRepository.java` | 为减少 H2 查询，全局设置会缓存一段时间（默认 60 秒），更新后可能存在秒级延迟。 |

---

## 水平扩展时的影响

当运行多个 `damning-proxy` 实例（多容器、多副本）时：

1. **熔断器不共享**：A 实例判定某上游已熔断，B 实例仍会继续向该上游发送请求。
2. **限流不共享**：总请求量会被拆分到多个实例的计数器中，实际限流阈值高于设定值。
3. **插件缓存各自独立**：每个实例都会独立编译并缓存脚本，启动后首次执行仍会有编译开销。
4. **全局设置缓存延迟**：修改 `/api/settings/rate-limit` 后，各实例将在缓存过期后陆续生效，最多延迟 `damning-proxy.global-settings.cache-ttl-seconds`（默认 60 秒）。

---

## 推荐做法

- **单实例部署**：默认使用方式，无需额外处理。
- **多实例 + 粘性会话**：如果前端或客户端能够固定路由到同一实例，可在一定程度上缓解限流/熔断不一致的问题，但无法完全解决。
- **外部化状态**：如需严格的多副本一致性，需要自行扩展实现：
  - 使用 Redis 等共享存储维护熔断器与限流计数器。
  - 使用集中式缓存或消息通知让全局设置变更即时生效。

> 当前版本未内置 Redis 扩展；如需分布式部署，请把上述状态外置作为后续定制点。
