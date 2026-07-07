[English Version](02-monitoring.en.md)

# 02 健康检查与监控

> 最后更新：2026-07-07  
> 对应源码版本：当前工作区

## 健康检查端点

`src/main/java/com/xenoamess/damning_proxy/api/HealthResource.java:11`

```bash
curl http://localhost:12360/v1/health
```

预期响应：

```json
{
  "status": "ok"|"degraded",
  "database": "ok"|"error",
  "upstreams": {
    "profile-slug": {
      "enabled": true,
      "status": "up"|"down",
      "statusCode": 200,
      "baseUrl": "https://api.openai.com/v1"
    }
  },
  "circuitBreakers": { ... }
}
```

说明：

- `status` 为 `ok` 当且仅当数据库连接正常 **且** 所有已启用（`enabled=true`）的 profile 上游可连通。
- 对每个已启用的 profile，健康检查会向其 `baseUrl + /models` 发送一个 5 秒超时的 GET 探测请求。
- 上游返回 HTTP 状态码 < 500 视为 `up`（包括 401/403/404 等可到达响应）；连接失败或返回 5xx 视为 `down`。
- 已禁用的 profile 会显示 `enabled: false`、`status: "disabled"`，不参与整体状态判定。
- `circuitBreakers` 返回当前各上游熔断器快照。

---

## Prometheus 指标

项目已集成 `quarkus-micrometer` 与 `quarkus-micrometer-registry-prometheus`，默认通过 `/q/metrics` 暴露 Prometheus 指标。

```bash
curl http://localhost:12360/q/metrics
```

### 主要指标

| 指标名 | 类型 | 标签 | 说明 |
|---|---|---|---|
| `damning_proxy_requests_total` | Counter | `instance`, `path`, `status` | 代理端点请求总数（按实例、路径、状态码统计） |
| `damning_proxy_request_duration_seconds` | Timer | `instance`, `path` | 代理端点请求处理耗时 |
| `damning_upstream_requests_total` | Counter | `baseUrl`, `status` | 上游 HTTP 请求总数（含最终状态码） |
| `damning_upstream_request_duration_seconds` | Timer | `baseUrl` | 上游请求耗时（含重试） |
| `damning_tokens_total` | Counter | `instance`, `type` | Token 用量（`type=prompt/completion/total`） |
| `damning_rate_limit_requests_total` | Counter | `instance`, `result` | 限流结果（`acquired` / `rejected`） |
| `damning_circuit_breaker_state` | Gauge | `baseUrl` | 熔断器状态（`0=closed`, `1=open`, `2=half_open`） |

说明：

- 代理请求指标在 `TrafficLogService.recordResponse()` 中采集，覆盖非流式与流式请求的最终响应。
- 上游指标在 `UpstreamHttpClient.send()` 中采集，反映经过重试后的最终上游调用结果。
- Token 用量从上游响应的 `usage` 字段提取，仅在上游返回标准 OpenAI 格式时产生。

### 配置

`src/main/resources/application.properties`：

```properties
quarkus.micrometer.enabled=true
quarkus.micrometer.export.prometheus.enabled=true
```

如需关闭 Prometheus 导出，设为 `false` 即可。

---

## 可用于监控的指标

当前版本已集成 Micrometer/Prometheus：

### 1. 健康检查

定期请求 `/v1/health`，状态码 200 表示服务存活。

### 2. Prometheus 指标

抓取 `/q/metrics` 获取 QPS、延迟、错误率、token 用量、熔断器状态等。

### 3. 日志

查看应用日志中的错误和上游请求失败信息。

```text
ERROR [com.xenoamess.damning_proxy.filter.GlobalExceptionMapper] Unhandled exception
ERROR [com.xenoamess.damning_proxy.proxy.UpstreamHttpClient] Upstream request failed
```

### 4. 流量日志

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

- 每写入 100 条日志检查一次总数与存活天数
- 超出 `damning-proxy.log.max-count`（默认 100000）时自动删除最旧的日志
- 超出 `damning-proxy.log.max-age-days`（默认 30 天）时自动删除超期日志
- 可通过配置项调整阈值（设为 0 或负数可禁用对应维度）

