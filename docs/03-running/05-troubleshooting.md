# 05 常见问题排查

> 最后更新：2026-06-17  
> 对应源码版本：当前工作区

## 启动问题

### 端口被占用

```text
BindException: Address already in use
```

解决：

```bash
mvn quarkus:dev -Dquarkus.http.port=8080
```

---

### JDK 版本错误

```text
Java 21 is required. Tests and runtime use libraries that are not compatible with newer JDKs.
```

解决：

```bash
java -version  # 确认是 21
export JAVA_HOME=/path/to/jdk21
```

---

### H2 数据库锁定

开发模式下如果 H2 文件数据库被多个进程占用，可能出现锁定错误。

解决：

- 停止其他进程。
- 删除 `${user.home}/.damning-proxy/data.lock.db`。
- 或使用内存 H2 临时测试。

---

## 构建问题

### 前端构建失败

常见原因：

- Node/npm 下载失败（网络问题）。
- `admin-web/package-lock.json` 与 `package.json` 不一致。

解决：

```bash
cd admin-web
rm -rf node_modules package-lock.json
npm install
npm run build
```

### Native 构建失败

- 确认 GraalVM 21 已安装并设置 `JAVA_HOME`。
- 检查是否缺少反射/资源配置文件。
- 查看 `target/native-image-output/` 下的报告。

---

## 代理问题

### 404 Instance not found

- 检查 `instanceSlug` 是否正确。
- 检查 Instance 是否启用。
- 检查是否创建了 Instance（StartupMigration 会在无 Instance 时自动从 Profile 创建，但若手动删除可能不存在）。

### 403 Instance disabled / Upstream profile disabled

- 检查 Instance 和 Profile 的 `enabled` 字段。

### 502 Upstream request failed

- 检查 Profile 的 `baseUrl` 是否正确。
- 检查上游服务是否可达。
- 检查 `bearerToken` 是否有效。
- 查看日志中的上游请求地址和错误信息。

### 非流式端点收到 400（streaming not supported）

- 请求体中 `stream: true`，但调用了 `Accept: application/json` 的非流式端点。
- 流式请求需要设置 `Accept: text/event-stream`。

---

## 插件问题

### 插件修改后未生效

- Groovy/JS 引擎按 `script` 内容缓存脚本，修改后需重启服务才能生效。

### 插件报错但流水线继续

- `PluginExecutionService` 会捕获插件异常并记录，不中断后续插件。
- 查看 `/api/logs/{id}/friendly` 中的插件错误信息。

### Groovy/JS 脚本语法问题

- 先在 `PluginEngineTest` 风格的小用例中测试脚本片段。
- 注意 Groovy 对 Map/List 的操作方式与 JS 不同。

---

## 日志问题

### 日志没有记录

- 检查请求是否到达代理端点。
- 检查数据库连接是否正常。
- 检查 `TrafficLogService` 是否因异常未提交事务。

### 日志内容被截断

- `TrafficLogService` 对 body/header/pluginLogs/snapshots 有长度上限。
- 大响应会被截断，详见 [02-design/01-data-model.md](../02-design/01-data-model.md)。

---

## 调试技巧

1. 开启 DEBUG 日志：

```properties
quarkus.log.category."com.xenoamess.damning_proxy".level=DEBUG
```

2. 查看上游请求地址：日志中搜索 `Upstream request:`。

3. 查看完整异常：`GlobalExceptionMapper` 会打印所有未捕获异常。
