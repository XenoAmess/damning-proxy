# 03 插件系统

> 最后更新：2026-06-17  > 对应源码版本：当前工作区

## 插件能做什么

插件是一段 Groovy 或 JavaScript 脚本，在代理请求或响应阶段执行，可：

- 读取/修改请求体、请求头
- 读取/修改响应体、响应头、响应状态码
- 记录日志
- 终止后续插件执行
- 直接返回响应，跳过上游请求

---

## 插件上下文 API

插件脚本通过 `context` 对象操作请求/响应。

完整类：`src/main/java/com/xenoamess/damning_proxy/plugin/PluginContext.java`

| 方法/属性 | 说明 |
|---|---|
| `context.getRequestBody()` / `context.setRequestBody(obj)` | 请求体读写 |
| `context.getRequestHeaders()` / `context.getRequestHeader(key)` / `context.setRequestHeader(key, value)` | 请求头读写 |
| `context.getResponseBody()` / `context.setResponseBody(obj)` | 响应体读写 |
| `context.getResponseHeaders()` / `context.getResponseHeader(key)` / `context.setResponseHeader(key, value)` | 响应头读写 |
| `context.getResponseStatus()` / `context.setResponseStatus(code)` | 响应状态码读写 |
| `context.log(message)` | 记录插件日志 |
| `context.stop()` | 终止后续插件执行 |
| `context.isStopped()` | 是否已 stop |
| `context.returnResponse(status, body, headers)` | 直接返回响应，跳过上游 |
| `context.isReturned()` | 是否已 returnResponse |

---

## 插件执行阶段

`src/main/java/com/xenoamess/damning_proxy/entity/Plugin.java:54`

| 阶段 | 说明 |
|---|---|
| `REQUEST` | 只在请求阶段执行 |
| `RESPONSE` | 只在响应阶段执行 |
| `BOTH` | 请求和响应阶段都会执行 |

同一个 `BOTH` 插件在请求和响应阶段各执行一次，脚本内部可通过 `context.getResponseStatus()` 等字段判断当前处于哪一阶段。

---

## 插件执行服务

`src/main/java/com/xenoamess/damning_proxy/plugin/PluginExecutionService.java:11`

执行规则：

1. 按 `PluginGroup.sortedItems()` 顺序遍历插件。
2. 跳过未启用的插件或 group item。
3. 若 `context.isStopped()` 或 `context.isReturned()`，停止后续插件。
4. 根据当前阶段（REQUEST / RESPONSE）匹配插件的 `executionPhase`。
5. 查找对应语言的 `PluginEngine` 执行脚本。
6. 捕获异常，记录到日志和 friendly snapshot，不中断流水线。

执行前后会生成 `PluginExecutionSnapshot`，记录插件名、阶段、输入、输出、错误信息。

---

## 插件引擎

### Groovy 引擎

`src/main/java/com/xenoamess/damning_proxy/plugin/engine/GroovyPluginEngine.java:15`

```text
GroovyShell shell
scriptCache: Map<String, Script>   // 按 script 内容缓存

execute(plugin, context)
  ├─ 从缓存获取或解析 Script
  ├─ Binding binding
  ├─ binding.setVariable("context", context)
  ├─ script.setBinding(binding)
  └─ script.run()
```

### JavaScript 引擎

`src/main/java/com/xenoamess/damning_proxy/plugin/engine/JavaScriptPluginEngine.java:15`

```text
sourceCache: Map<String, Source>   // 按 script 内容缓存

execute(plugin, context)
  ├─ 从缓存获取或构建 Source
  ├─ Context.newBuilder("js").allowAllAccess(true).build()
  ├─ bindings.putMember("context", context)
  └─ jsContext.eval(source)
```

### 缓存说明

- Groovy 和 JS 引擎都按 `plugin.script` 字符串内容缓存已编译的脚本。
- **修改插件脚本后，缓存不会自动失效**，需要重启服务才会生效（或实现缓存失效逻辑）。

---

## 插件示例

### JS：修改请求模型名

```javascript
var body = context.getRequestBody();
body.model = 'gpt-4o';
context.setRequestBody(body);
context.log('rewrote model to gpt-4o');
```

### Groovy：直接返回自定义响应

```groovy
context.returnResponse(200, [message: 'intercepted'], ['X-Custom': 'yes'])
```

更多示例见 [05-guides/02-plugin-development.md](../05-guides/02-plugin-development.md)。

---

## 插件执行流程图

```text
请求进入
  │
  ▼
┌─────────────────────────────┐
│ 执行 REQUEST / BOTH 阶段插件  │
│ 按 orderIndex → priority → id │
└─────────────────────────────┘
  │
  ├─ 插件调用 context.returnResponse()
  │     └─ 跳过上游，直接作为响应返回
  │
  ├─ 插件调用 context.stop()
  │     └─ 终止后续插件，但继续转发到上游
  │
  └─ 所有插件执行完毕
        │
        ▼
  转发到上游 OpenAI 源
        │
        ▼
┌─────────────────────────────┐
│ 执行 RESPONSE / BOTH 阶段插件 │
│ 按 orderIndex → priority → id │
└─────────────────────────────┘
        │
        ▼
  返回客户端
```

对于流式响应，响应阶段插件在 SSE 完整接收后执行，操作的是累积内容。
