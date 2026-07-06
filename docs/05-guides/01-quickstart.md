[English Version](01-quickstart.en.md)

# 01 快速开始

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区

## 5 分钟跑起来

### 1. 确认环境

```bash
java -version  # 需要 JDK 21
mvn -version   # 需要 Maven 3.9+
```

### 2. 启动开发模式

```bash
mvn quarkus:dev
```

首次启动会自动下载 Node/npm 并构建前端，耗时约 1-3 分钟。

### 3. 打开管理后台

浏览器访问：

```text
http://localhost:12360/
```

会自动跳转到 `/admin/index.html`。

---

## 配置你的第一个上游

1. 进入「上游配置」页面。
2. 点击「新建」，填写：
   - 名称：`OpenAI`
   - Slug：`openai`
   - Base URL：`https://api.openai.com/v1`
   - Bearer Token：你的 OpenAI API Key
   - 默认模型：`gpt-4o`
   - 超时：30000
3. 保存。

---

## 创建第一个实例

1. 进入「实例管理」页面。
2. 点击「新建」，填写：
   - 名称：`My Instance`
   - Slug：`my-instance`
   - 上游配置：选择刚才创建的 `openai`
   - 插件组：选择 `sample-js` 或 `sample-groovy`（启动时自动创建）
3. 保存。

实例创建后，页面上会显示可复制的 OpenAI URL：

```text
http://localhost:12360/v1/proxy/my-instance
```

---

## 用 curl 测试

### 模型列表

```bash
curl http://localhost:12360/v1/proxy/my-instance/models
```

### 聊天补全

```bash
curl -H "Authorization: Bearer sk-test" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "Hello"}]
  }' \
  http://localhost:12360/v1/proxy/my-instance/chat/completions
```

### 流式

```bash
curl -N -H "Authorization: Bearer sk-test" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": true
  }' \
  http://localhost:12360/v1/proxy/my-instance/chat/completions
```

---

## 查看流量日志

每次请求后，进入「流量日志」页面：

- 查看请求/响应报文。
- 查看插件执行流水线。
- 查看插件日志。
- 删除或清空日志。

---

## 下一步

- 学习插件开发：[02 插件开发指南](02-plugin-development.md)
- 接入 OpenCode：[03 接入 OpenCode](03-connect-opencode.md)
- 查看 API 文档：[04 API 文档](../04-api/)
