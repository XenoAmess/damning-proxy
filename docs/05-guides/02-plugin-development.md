[English Version](02-plugin-development.en.md)

# 02 插件开发指南

> 最后更新：2026-07-07  
> 对应源码版本：当前工作区

## 插件基础

插件是一段 Groovy 或 JavaScript 脚本，在代理请求/响应阶段执行。脚本通过全局变量 `context` 访问和修改请求/响应。

完整 API 见 [02-design/03-plugin-system.md](../02-design/03-plugin-system.md)。

---

## context API 速查

| 方法 | 说明 |
|---|---|
| `context.getRequestBody()` / `context.setRequestBody(obj)` | 请求体读写 |
| `context.getRequestHeaders()` / `context.setRequestHeader(k, v)` | 请求头读写 |
| `context.getResponseBody()` / `context.setResponseBody(obj)` | 响应体读写 |
| `context.getResponseHeaders()` / `context.setResponseHeader(k, v)` | 响应头读写 |
| `context.getResponseStatus()` / `context.setResponseStatus(code)` | 响应状态码读写 |
| `context.log(message)` | 记录插件日志 |
| `context.stop()` | 终止后续插件执行 |
| `context.returnResponse(status, body, headers)` | 直接返回响应，跳过上游 |

---

## 执行阶段

创建插件时选择执行阶段：

- `REQUEST`：只在请求阶段执行
- `RESPONSE`：只在响应阶段执行
- `BOTH`：请求和响应阶段都执行

---

## JavaScript 插件示例

### 修改请求模型名

```javascript
var body = context.getRequestBody();
body.model = 'gpt-4o';
context.setRequestBody(body);
context.log('rewrote model to gpt-4o');
```

### 追加 system prompt

```javascript
const body = context.getRequestBody();
if (!body || !Array.isArray(body.messages)) return;

const hint = '请用简洁的中文回答。';
const systemMessage = body.messages.find(m => m && m.role === 'system');

if (systemMessage && typeof systemMessage.content === 'string') {
  systemMessage.content += '\n' + hint;
} else {
  body.messages.unshift({ role: 'system', content: hint });
}
context.log('appended system hint');
```

### 直接返回响应（mock）

```javascript
context.returnResponse(200, {
  id: 'mock',
  model: 'gpt-4o',
  choices: [{
    message: { role: 'assistant', content: '这是插件直接返回的内容。' }
  }]
}, { 'X-Mocked': 'true' });
```

---

## Groovy 插件示例

### 修改请求模型名

```groovy
def body = context.getRequestBody()
body.model = 'gpt-4o'
context.setRequestBody(body)
context.log('rewrote model to gpt-4o')
```

### 追加 system prompt

```groovy
def body = context.getRequestBody()
if (body == null) return

def messages = body.get("messages")
if (!(messages instanceof List)) return

def hint = "请用简洁的中文回答。"
def systemMessage = null

for (def m : messages) {
  if (m == null) continue
  if ("system".equals(m.get("role"))) {
    systemMessage = m
    break
  }
}

if (systemMessage != null) {
  def content = systemMessage.get("content")
  if (content instanceof String) {
    systemMessage.put("content", content + "\n" + hint)
  }
} else {
  def newSystem = new LinkedHashMap()
  newSystem.put("role", "system")
  newSystem.put("content", hint)
  messages.add(0, newSystem)
}
context.log('appended system hint')
```

### 直接返回响应

```groovy
context.returnResponse(200,
  [id: 'mock', model: 'gpt-4o', choices: [[message: [role: 'assistant', content: 'Groovy mocked']]]],
  ['X-Mocked': 'true'])
```

---

## 响应阶段插件

响应阶段可以修改上游返回的内容，例如替换模型名：

```javascript
var body = context.getResponseBody();
if (body && body.model) {
  body.model = 'custom-model';
  context.setResponseBody(body);
  context.log('rewrote response model');
}
```

---

## 插件组配置

1. 进入「插件组」页面。
2. 创建插件组，选择要组合的插件。
3. 调整 `orderIndex` 和 `priority`。
4. 在「实例管理」中把实例绑定到该插件组。

执行顺序：`orderIndex` 升序 → `priority` 升序 → `id` 升序。

---

## 调试插件

1. 在脚本中使用 `context.log('...')` 输出调试信息。
2. 发起代理请求。
3. 进入「流量日志」→ 点击详情 → 查看「插件流水线」和「插件日志」。

---

## 注意事项

- 插件脚本按内容哈希缓存，保存修改后下一次请求会自动重新编译并生效，无需重启服务。
- 插件拥有完整 JVM 权限，不要运行来源不明的脚本。
- 响应阶段插件在流式请求中操作的是累积后的完整响应体。
- 如果插件抛出异常，不会中断流水线，但会记录到 friendly snapshot 中。

---

## 内置示例插件

启动时自动创建两个示例插件：

- `大明战锤提示词（Groovy）`
- `大明战锤提示词（JS）`

它们都会在请求阶段把固定 system prompt 追加到消息列表。

源码：`src/main/java/com/xenoamess/damning_proxy/migration/StartupMigration.java:113`
