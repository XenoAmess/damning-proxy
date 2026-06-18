# 01 流量日志

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区

## 日志记录范围

每次代理请求都会在 `TrafficLog` 表中记录：

- 请求方法、路径、头、体
- 响应状态码、头、体
- 请求/响应时间、耗时
- 插件日志
- 插件执行快照（friendly snapshots）

实体定义：`src/main/java/com/xenoamess/damning_proxy/entity/TrafficLog.java`

**注意**：请求头中的 `Authorization` 值会被脱敏为 `Bearer ***`，避免将上游 Token 明文写入日志。

---

## 记录时机

`src/main/java/com/xenoamess/damning_proxy/proxy/OpenAiProxyService.java`

| 步骤 | 行为 |
|---|---|
| 请求进入 | 调用 `TrafficLogService.recordRequest(...)` 创建日志 |
| 请求阶段插件执行后 | 若直接返回，调用 `recordResponse(...)` |
| 上游响应后 | 调用 `recordResponse(...)` 更新响应信息 |
| 流式响应结束 | 异步调用 `recordResponse(..., 200, ...)` |

---

## 日志长度限制

`src/main/java/com/xenoamess/damning_proxy/service/TrafficLogService.java:27`

| 字段 | 最大长度 |
|---|---|
| requestHeaders / responseHeaders | 2000 |
| requestBody / responseBody | 10000 |
| pluginLogs | 5000 |
| friendlyPluginSnapshots | 8000 |

超出后会被截断并追加 `...[truncated]`。

---

## 查询日志

### 管理 API

```bash
# 最近 100 条
curl http://localhost:12360/api/logs

# 按 instance 筛选
curl "http://localhost:12360/api/logs?instanceId=1&limit=50"

# 按 profile 筛选
curl "http://localhost:12360/api/logs?profileId=1&limit=50"

# 单条日志
curl http://localhost:12360/api/logs/1

# 友好格式
curl http://localhost:12360/api/logs/1/friendly
```

### Web 管理后台

进入「流量日志」页面：

- 卡片式列表展示最近日志。
- 点击卡片查看详情。
- 详情包含：对话摘要、请求插件流水线、响应插件流水线、原始请求/响应、插件日志。
- 支持删除单条日志和清空全部日志。

---

## 日志清理

```bash
# 删除单条
curl -X DELETE http://localhost:12360/api/logs/1

# 清空全部
curl -X POST http://localhost:12360/api/logs/clear
```

---

## 友好日志字段

`src/main/java/com/xenoamess/damning_proxy/api/admin/LogAdminApi.java:77`

友好日志从原始日志中提取：

- `userPrompt`：用户提示词
- `modelOutput`：模型输出
- `model`：请求模型名
- `requestPipeline`：请求阶段插件快照列表
- `responsePipeline`：响应阶段插件快照列表

每个快照包含：插件名、阶段、输入、输出、是否错误、错误信息。

---

## 数据库存储

默认 H2 文件数据库路径：

```text
${user.home}/.damning-proxy/data.mv.db
```

如需长期保留日志，请定期备份或切换到 PostgreSQL/MySQL。
