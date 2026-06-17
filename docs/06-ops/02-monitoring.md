# 02 健康检查与监控

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区

## 健康检查端点

`src/main/java/com/xenoamess/damning_proxy/api/HealthResource.java:11`

```bash
curl http://localhost:12360/v1/health
```

预期响应：

```json
{ "status": "ok" }
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

## 未来改进

- 集成 `quarkus-micrometer` 暴露 Prometheus 指标。
- 统计 QPS、延迟分位值、上游错误率。
- 增加数据库连接池健康检查。
