# 02 健康检查与监控

> 最后更新：2026-06-21  
> 对应源码版本：当前工作区

## 健康检查端点

`src/main/java/com/xenoamess/damning_proxy/api/HealthResource.java:11`

```bash
curl http://localhost:12360/v1/health
```

预期响应：

```json
{ "status": "ok"|"degraded", "database": "ok"|"error" }
```

---

## 可用于监控的指标

当前版本未集成 Micrometer/Prometheus，可通过以下方式监控：

### 1. 健康检查

定期请求 `/v1/health`，状态码 200 表示服务存活。

### 2. 日志

查看应用日志中的错误和上游请求失败信息。

```text
ERROR [com.xenoamess.damning_proxy.filter.GlobalExceptionMapper] Unhandled exception
ERROR [com.xenoamess.damning_proxy.proxy.UpstreamHttpClient] Upstream request failed
```

### 3. 流量日志

通过 `/api/logs` 分析请求量、错误率、平均耗时。

---

## 日志级别调整

`src/main/resources/application.properties:19`

```properties
quarkus.log.level=INFO
quarkus.log.category."com.xenoamess.damning_proxy".level=DEBUG
```

如需更详细的上游请求日志，项目包内日志已开启 DEBUG，会打印：

```text
DEBUG [com.xenoamess.damning_proxy.proxy.UpstreamHttpClient] Upstream request: POST https://api.openai.com/v1/chat/completions
```

---

## 已实现功能

### 熔断器 (CircuitBreaker)

`src/main/java/com/xenoamess/damning_proxy/proxy/CircuitBreaker.java`

每个上游 baseUrl 独立熔断：
- 连续失败 3 次 → 熔断器打开，拒绝后续请求
- 打开 30 秒后 → 半开状态，允许一次探测请求
- 探测成功 → 恢复关闭；探测失败 → 重新打开

### 频率限制 (RateLimiter)

`src/main/java/com/xenoamess/damning_proxy/proxy/RateLimiter.java`

滑动窗口频率限制，按实例 slug 计数：
- 默认每 60 秒窗口内每实例最多 60 个请求
- 可通过 `damning-proxy.rate-limit.max-requests` 和 `damning-proxy.rate-limit.window-seconds` 调整
- 超限请求返回 HTTP 429

### 日志保留策略

`src/main/java/com/xenoamess/damning_proxy/service/TrafficLogService.java`

- 每写入 100 条日志检查一次总数
- 超出 `damning-proxy.log.max-count`（默认 100000）时自动删除最旧的日志
- 可通过配置项调整阈值

---

## 未来改进

- 集成 `quarkus-micrometer` 暴露 Prometheus 指标。
- 统计 QPS、延迟分位值、上游错误率。
