# daming-proxy（大明proxy）

一个基于 **Java 21**、**Quarkus**、**GraalVM Native Image** 的 OpenAI 协议代理服务器。

大明proxy 可以代理多个上游 OpenAI 接口，并通过 **Groovy / JavaScript 插件** 按顺序篡改出入报文，实现自定义的模型映射、请求改写、响应替换、日志审计等功能。

## 特性

- **OpenAI 协议代理**：对外暴露 OpenAI 兼容接口，转发到配置的上游源
- **多配置管理**：支持配置多个上游 Profile，按子 URL 路由
- **插件化报文篡改**：支持 Groovy / JavaScript 脚本插件，按顺序执行
- **全量流量日志**：自动记录所有请求/响应，支持 Web 页增删改查
- **Web 管理后台**：基于 Vue 3 + Vite + Element Plus 的本地管理界面
- **GraalVM Native Image** 支持

## 技术栈

| 技术 | 版本 |
|-----|------|
| Java | 21 |
| Quarkus | 3.15.1 |
| Maven | 3.9+ |
| H2 | （默认，便于切换 PG/MySQL） |
| Vue | 3 |
| Vite | 6 |
| Element Plus | 2 |

## 快速开始

### 1. 开发模式运行

```bash
mvn quarkus:dev
```

服务启动在 `http://localhost:12360`。

浏览器访问 `http://localhost:12360/` 会自动跳转到管理后台。

### 2. 构建并运行 JAR

```bash
mvn clean package
java -jar target/daming-proxy-1.0-SNAPSHOT-runner.jar
```

Maven 构建会自动安装 Node、npm 依赖并构建前端界面。

### 3. 构建 Native Image（需要 GraalVM）

```bash
mvn clean package -Pnative
./target/daming-proxy-1.0-SNAPSHOT-runner
```

## 代理端点
https://api.minimaxi.com/v1
所有代理端点都基于 Profile 的 `slug`：

| Method | Path | 说明 |
|--------|------|------|
| `POST` | `/v1/proxy/{slug}/chat/completions` | 聊天补全（支持 SSE 流式） |
| `GET`  | `/v1/proxy/{slug}/models` | 模型列表 |

示例：

```bash
curl -H "Authorization: Bearer sk-test" \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-4","messages":[{"role":"user","content":"Hello"}],"stream":false}' \
  http://localhost:12360/v1/proxy/openai/chat/completions
```

## Web 管理后台

启动后访问 `http://localhost:12360/admin/index.html`。

后台支持：

- **上游配置**：配置多个 OpenAI 源，包含 baseURL、Bearer Token、自定义 Header、超时等
- **插件管理**：编写 Groovy / JS 插件，设置执行阶段（请求/响应/两者）、优先级、作用域
- **流量日志**：查看完整请求/响应、插件日志，支持删除和清空

## 插件开发

插件通过 `context` 对象访问和修改请求/响应：

```javascript
// JS 示例：修改请求模型名
var body = context.getRequestBody();
body.model = 'gpt-4o';
context.setRequestBody(body);
context.log('rewrote model to gpt-4o');
```

```groovy
// Groovy 示例：直接返回自定义响应
context.returnResponse(200, [message: 'intercepted'], ['X-Custom': 'yes'])
```

### context API

| 方法 | 说明 |
|------|------|
| `getRequestBody()` / `setRequestBody(obj)` | 请求体读写 |
| `getRequestHeaders()` / `setRequestHeader(k, v)` | 请求头读写 |
| `getResponseBody()` / `setResponseBody(obj)` | 响应体读写 |
| `getResponseHeaders()` / `setResponseHeader(k, v)` | 响应头读写 |
| `getResponseStatus()` / `setResponseStatus(code)` | 响应状态码读写 |
| `log(message)` | 记录插件日志 |
| `stop()` | 终止后续插件执行 |
| `returnResponse(status, body, headers)` | 直接返回响应，跳过上游 |

## 连接 OpenCode

在 `opencode.json` 中配置大明proxy 作为自定义 provider：

```json
{
  "$schema": "https://opencode.ai/config.json",
  "provider": {
    "daming-proxy": {
      "npm": "@ai-sdk/openai-compatible",
      "name": "大明proxy (local)",
      "options": {
        "baseURL": "http://localhost:12360/v1/proxy/openai",
        "apiKey": "sk-test"
      },
      "models": {
        "gpt-4": { "name": "GPT-4" },
        "gpt-4o": { "name": "GPT-4o" }
      }
    }
  }
}
```

`models` 中的 ID 需要与上游 `/v1/models` 返回的模型 ID 一致。

## 配置说明

### application.properties

```properties
quarkus.http.port=12360

# 数据库（默认 H2 文件型，可切换为 PostgreSQL/MySQL）
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:file:${user.home}/.daming-proxy/data

# CORS
quarkus.http.cors=true
quarkus.http.cors.origins=*
```

### 环境变量

| 变量 | 说明 |
|------|------|
| `API_TOKEN` | 旧全局认证 token（已不再使用，各 Profile 独立配置 token） |

## 项目结构

```
src/
├── main/
│   ├── java/com/xenoamess/daming_proxy/
│   │   ├── api/              # REST API（代理、管理、首页）
│   │   ├── api/admin/        # 管理后台 REST API
│   │   ├── entity/           # 领域实体
│   │   ├── plugin/           # 插件上下文与执行引擎
│   │   ├── plugin/engine/    # Groovy / JS 引擎
│   │   ├── proxy/            # OpenAI 代理核心
│   │   ├── repository/       # 仓库接口
│   │   ├── repository/panache/ # Panache H2 实现
│   │   └── service/          # 业务服务（日志服务）
│   └── resources/
│       ├── application.properties
│       └── META-INF/resources/admin/  # 前端构建产物
├── test/
│   └── java/.../             # 单元测试与集成测试
└── admin-web/                # Vue 3 管理后台源码
```

## 开发

### 运行测试

```bash
mvn test
```

### 前端开发

```bash
cd admin-web
npm install
npm run dev
```

开发服务器代理 `/api` 到 `http://localhost:12360`。

## License

MIT
