# 04 自测方式

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区

## 单元测试与集成测试

### 运行全部测试

```bash
mvn test
```

- 执行 `src/test/java` 下的所有 JUnit 5 测试。
- 使用内存 H2，`src/test/resources/application.properties`。
- 集成测试使用 WireMock 模拟上游 OpenAI 服务。
- **环境要求 JDK 21**，其他版本可能导致 Byte Buddy / GraalJS 初始化失败。

### Native Image 测试

```bash
mvn clean verify -Pnative
```

- 会构建 Native Image 并运行集成测试。
- 耗时较长，需要 GraalVM。

---

## 测试文件清单

| 测试类 | 覆盖范围 | 文件 |
|---|---|---|
| `AdminApiTest` | 管理后台 CRUD（Profile/Plugin/Group/Log） | `src/test/java/com/xenoamess/damning_proxy/api/admin/AdminApiTest.java` |
| `ProxyApiTest` | 代理转发、自定义 Header、流量日志 | `src/test/java/com/xenoamess/damning_proxy/proxy/ProxyApiTest.java` |
| `PluginEngineTest` | Groovy/JS 插件引擎基本能力 | `src/test/java/com/xenoamess/damning_proxy/plugin/PluginEngineTest.java` |
| `PanacheLogRepositoryTest` | 日志仓库 | `src/test/java/com/xenoamess/damning_proxy/repository/PanacheLogRepositoryTest.java` |
| `PanachePluginRepositoryTest` | 插件仓库 | `src/test/java/com/xenoamess/damning_proxy/repository/PanachePluginRepositoryTest.java` |
| `PanacheProfileRepositoryTest` | 上游配置仓库 | `src/test/java/com/xenoamess/damning_proxy/repository/PanacheProfileRepositoryTest.java` |
| `TrafficLogServiceTest` | 日志服务 | `src/test/java/com/xenoamess/damning_proxy/service/TrafficLogServiceTest.java` |

---

## 手动自测

### 1. 启动服务

```bash
mvn quarkus:dev
```

### 2. 健康检查

```bash
curl http://localhost:12360/v1/health
```

预期返回：

```json
{ "status": "ok" }
```

### 3. 创建上游 Profile

```bash
curl -X POST http://localhost:12360/api/profiles \
  -H "Content-Type: application/json" \
  -d '{
    "name": "OpenAI",
    "slug": "openai",
    "baseUrl": "https://api.openai.com/v1",
    "bearerToken": "sk-xxxxxxxx",
    "timeoutMs": 30000,
    "enabled": true
  }'
```

### 4. 创建插件组

```bash
curl -X POST http://localhost:12360/api/plugin-groups \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Default Group",
    "slug": "default",
    "description": "default group",
    "enabled": true,
    "items": []
  }'
```

### 5. 创建 Instance

假设 profile id = 1，group id = 1：

```bash
curl -X POST http://localhost:12360/api/instances \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Instance",
    "slug": "my-instance",
    "profileId": 1,
    "pluginGroupId": 1,
    "enabled": true
  }'
```

### 6. 调用代理

```bash
curl -H "Authorization: Bearer sk-test" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": false
  }' \
  http://localhost:12360/v1/proxy/my-instance/chat/completions
```

### 7. 查看日志

```bash
curl http://localhost:12360/api/logs
```

---

## 流式接口自测

```bash
curl -N -H "Authorization: Bearer sk-test" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": true
  }' \
  http://localhost:12360/v1/proxy/my-instance/chat/completions
```

---

## Web 管理后台自测

1. 启动服务后访问 `http://localhost:12360/admin/index.html`。
2. 在「上游配置」页面创建 Profile。
3. 在「插件管理」页面编写插件。
4. 在「插件组」页面组合插件并调整顺序。
5. 在「实例管理」页面创建实例并绑定 Profile 与插件组。
6. 在「对话测试」页面选择实例进行流式/非流式对话。
7. 在「流量日志」页面查看完整请求/响应与插件流水线。

---

## 测试上游（Mock）

如果不想连接真实 OpenAI，可用 WireMock 或 Python 快速起一个 mock：

```python
from flask import Flask, request, Response
import json

app = Flask(__name__)

@app.route('/v1/models', methods=['GET'])
def models():
    return {"object": "list", "data": [{"id": "mock-gpt-4o", "object": "model"}]}

@app.route('/v1/chat/completions', methods=['POST'])
def chat():
    body = request.json
    if body.get('stream'):
        def generate():
            data = json.dumps({"choices": [{"delta": {"content": "hello"}}]})
            yield f"data: {data}\n\n"
            yield "data: [DONE]\n\n"
        return Response(generate(), mimetype='text/event-stream')
    return {"id": "chatcmpl-1", "model": body.get("model"), "choices": [{"message": {"role": "assistant", "content": "Hello!"}}]}

if __name__ == '__main__':
    app.run(port=18089)
```

把 Profile 的 `baseUrl` 设为 `http://localhost:18089/v1` 即可测试。

---

## 测试命令速查

| 场景 | 命令 |
|---|---|
| 运行全部测试 | `mvn test` |
| 运行单个测试类 | `mvn test -Dtest=ProxyApiTest` |
| Native 测试 | `mvn clean verify -Pnative` |
| 健康检查 | `curl http://localhost:12360/v1/health` |
| 创建 Profile | `curl -X POST http://localhost:12360/api/profiles ...` |
| 调用代理 | `curl http://localhost:12360/v1/proxy/{slug}/chat/completions ...` |
| 查看日志 | `curl http://localhost:12360/api/logs` |
