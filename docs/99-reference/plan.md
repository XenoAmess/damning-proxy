# 大明proxy 改造实施计划

> ⚠️ 这是初始设计文档，部分设计决策在实际实现中已调整。以当前代码和 API 文档为准。

## 项目目标
将当前 OpenAI 兼容 Mock 服务改造为 **插件化 OpenAI 协议代理服务器**。

- 对外暴露 OpenAI 兼容接口。
- 可配置多个上游源（Profile），按子 URL 路由。
- 支持 Groovy / JavaScript 插件，按顺序篡改出入报文。
- 提供本地 Web 管理页进行配置与日志管理。
- 全量记录出入报文日志，支持 Web 页 CRUD。

---

## 关键设计决策

| 项 | 决策 |
|---|---|
| 路由形式 | `/v1/proxy/{instanceSlug}/chat/completions`、`/v1/proxy/{instanceSlug}/models`（注：设计阶段使用 `{profileId}`，实际实现改为 `{instanceSlug}`） |
| 数据库 | H2（开发/本地），通过 Panache 抽象便于切换 PostgreSQL/MySQL |
| Web 管理页 | Vite + Vue 3 + Element Plus，构建后嵌入 Quarkus 静态资源 |
| 插件语言 | Groovy + JavaScript（GraalJS） |
| 上游认证 | 默认 Bearer Token，高级设置支持自定义 Header |
| 流式插件 | 支持整体缓冲修改和逐 chunk 修改两种模式 |

---

## 阶段一：基础设施

### 1.1 依赖
- `quarkus-hibernate-orm-panache`
- `quarkus-jdbc-h2`
- `quarkus-rest-client-reactive-jackson`
- `quarkus-vertx`
- `org.codehaus.groovy:groovy-jsr223`
- `org.graalvm.polyglot:js`
- `quarkus-qute`

### 1.2 领域模型

#### ProxyProfile
```java
id, name, slug（唯一）, baseUrl, bearerToken, customHeaders(JSON),
defaultModel, timeoutMs, enabled, createdAt, updatedAt
```

#### Plugin
```java
id, name, language(GROOVY/JS), script, priority,
executionPhase(REQUEST/RESPONSE/BOTH), enabled,
globalScope(true/false), profileId（nullable）, createdAt, updatedAt
```

#### TrafficLog
```java
id, profileId, requestPath, requestMethod,
requestHeaders, requestBody, requestTime,
responseStatus, responseHeaders, responseBody, responseTime,
durationMs, pluginLogs(JSON)
```

### 1.3 数据库抽象
- 定义 `LogRepository`、`ProfileRepository`、`PluginRepository` 接口。
- Panache 实现 H2 版本。

---

## 阶段二：OpenAI 代理核心

### 2.1 新端点
- `POST /v1/proxy/{instanceSlug}/chat/completions`
- `GET  /v1/proxy/{instanceSlug}/models`

### 2.2 处理流程
1. 解析 profileId，加载 ProxyProfile。
2. 请求阶段插件链执行。
3. 转发到上游 OpenAI 服务器。
4. 响应阶段插件链执行。
5. 记录 TrafficLog。
6. 返回客户端。

### 2.3 流式处理
- 非流式：body 整体过插件。
- 流式：默认逐 chunk 透传；若插件声明需要整体修改，则缓冲完整 SSE 后修改再下发。

---

## 阶段三：插件引擎

### 3.1 插件上下文
```java
context.request        // 请求体对象
context.requestHeaders // Map<String, String>
context.response       // 响应体对象
context.responseHeaders
context.responseStatus
context.log(message)   // 写入插件日志
context.stop()         // 终止后续插件
context.returnResponse(status, body, headers)
```

### 3.2 执行器
- `GroovyPluginEngine`
- `JavaScriptPluginEngine`
- 统一接口：`PluginEngine.execute(script, context)`

### 3.3 插件作用域
- 全局插件：所有 profile 生效。
- Profile 插件：仅指定 profile 生效。

---

## 阶段四：Web 管理页

### 4.1 前端
Vite + Vue 3 + Element Plus，构建产物输出到 `src/main/resources/META-INF/resources/admin/`。

页面：
- `/admin/index.html`
- Profile 管理
- Plugin 管理（带代码编辑器）
- Log 管理（列表、详情、清空、筛选）

### 4.2 后端 API
- `GET/POST/PUT/DELETE /api/profiles`
- `GET/POST/PUT/DELETE /api/plugins`
- `GET/DELETE /api/logs`
- `POST /api/logs/clear`

---

## 阶段五：日志全量记录

- 代理入口记录请求信息。
- 上游响应后记录响应信息。
- 插件 `context.log()` 追加到 `pluginLogs`。
- Web 页支持分页、按 profile/状态码/时间筛选。

---

## 阶段六：测试与文档

- 单元测试覆盖：Profile CRUD、插件引擎、代理转发、日志记录。
- 集成测试：WireMock 模拟上游 OpenAI。
- 更新 README、opencode.json、doc/ 设计文档。

---

## 执行顺序

1. 阶段一：依赖 + 领域模型 + 数据库配置
2. 阶段二：代理核心 + 测试
3. 阶段三：插件引擎 + 测试
4. 阶段五：日志记录 + 测试（与代理核心紧耦合，提前实现）
5. 阶段四：Web 管理页 + REST API
6. 阶段六：集成测试 + 文档
